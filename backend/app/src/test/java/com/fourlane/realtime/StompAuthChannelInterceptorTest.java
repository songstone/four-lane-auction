package com.fourlane.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.Test;

class StompAuthChannelInterceptorTest {
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final JwtAuthenticationConverter converter = converter();
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(decoder, converter);

    private static JwtAuthenticationConverter converter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> java.util.List.of(
                new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return converter;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/lanes/1", "/topic/lanes/123456", "/topic/lanes/0"})
    void allowsOnlyNumericLaneTopic(String destination) {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/queue/anything", "/user/queue/notifications", "/topic/ping",
            "/topic/lanes/1/extra", "/topic/lanes/*", "/topic/lanes/-1", "/topic/lanes/abc"})
    void deniesDestinationsOutsideAllowList(String destination) {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination(destination);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, null)).hasMessage("SUBSCRIPTION_DENIED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer ", "Bearer not-a-token"})
    void malformedTokenNeverDowngradesToAnonymous(String value) {
        when(decoder.decode(anyString())).thenThrow(new BadJwtException("invalid"));
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", value);
        headers.setLeaveMutable(true);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, null)).hasMessage("TOKEN_INVALID");
    }

    @Test
    void duplicateAuthorizationIsRejectedAndCredentialsAreRemoved() {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.addNativeHeader("Authorization", "Bearer first");
        headers.addNativeHeader("Authorization", "Bearer second");
        headers.setLeaveMutable(true);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, null)).hasMessage("TOKEN_INVALID");
        assertThat(headers.containsNativeHeader("Authorization")).isFalse();
        verifyNoInteractions(decoder);
    }

    @Test
    void anonymousConnectDoesNotInheritHttpUpgradeIdentity() {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setUser(() -> "untrusted-upgrade-identity");
        headers.setLeaveMutable(true);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
        assertThat(headers.getUser()).isNull();
        verifyNoInteractions(decoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ADMIN"})
    void authenticatedPrincipalHasMemberIdAndRoleAndMaySubscribe(String role) {
        var jwt = Jwt.withTokenValue("valid").header("alg", "HS256").subject("42").claim("role", role).build();
        when(decoder.decode("valid")).thenReturn(jwt);
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer valid");
        headers.setLeaveMutable(true);
        interceptor.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null);
        var authentication = (org.springframework.security.core.Authentication) headers.getUser();
        assertThat(authentication.getName()).isEqualTo("42");
        assertThat(authentication.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_" + role, "FACTOR_BEARER");
        assertThat(headers.containsNativeHeader("Authorization")).isFalse();
        var subscription = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        subscription.setUser(authentication);
        subscription.setDestination("/user/queue/notifications");
        var message = MessageBuilder.createMessage(new byte[0], subscription.getMessageHeaders());
        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }
}
