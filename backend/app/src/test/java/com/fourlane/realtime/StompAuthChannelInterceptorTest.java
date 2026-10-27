package com.fourlane.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

class StompAuthChannelInterceptorTest {
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor();

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
    void temporaryRestrictionNeverDowngradesTokenToAnonymous(String value) {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", value);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, null)).hasMessage("TOKEN_INVALID");
    }
}
