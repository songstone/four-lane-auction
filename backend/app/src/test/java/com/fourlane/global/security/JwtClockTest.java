package com.fourlane.global.security;

import static org.assertj.core.api.Assertions.*;

import com.fourlane.support.MutableClock;
import java.time.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

class JwtClockTest {
    static final Instant BASE = Instant.parse("2020-01-01T00:00:00Z"); // Deliberately different from wall clock.
    final MutableClock clock = new MutableClock(BASE);
    final JwtConfig config = new JwtConfig();
    final javax.crypto.SecretKey key = config.jwtSigningKey(new AuthProperties(
            Base64.getEncoder().encodeToString(new byte[64]), false));
    final JwtEncoder encoder = config.jwtEncoder(key);
    final JwtDecoder decoder = config.jwtDecoder(key, clock);

    @Test void usesInjectedClockAndRejectsAtExpiryNotSixtySecondsLater() {
        String value = token(b -> {});
        assertThat(decoder.decode(value).getSubject()).isEqualTo("1");
        clock.advance(Duration.ofMinutes(15).minusMillis(1));
        assertThatCode(() -> decoder.decode(value)).doesNotThrowAnyException();
        clock.advance(Duration.ofMillis(1));
        assertThatThrownBy(() -> decoder.decode(value)).isInstanceOf(JwtValidationException.class);
        clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> decoder.decode(value)).isInstanceOf(JwtValidationException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"exp", "iat", "iss", "aud", "sub", "role", "token_use"})
    void rejectsMissingRequiredClaims(String claim) {
        String value = token(b -> b.claims(c -> c.remove(claim)));
        assertThatThrownBy(() -> decoder.decode(value)).isInstanceOf(JwtException.class);
    }

    @Test void rejectsWrongIssuerAudienceRoleTypeAndFutureValidity() {
        List<Consumer<JwtClaimsSet.Builder>> mutations = List.of(
                b -> b.issuer("other"), b -> b.audience(List.of("other")), b -> b.subject("0"),
                b -> b.claim("role", "ROOT"), b -> b.claim("token_use", "refresh"),
                b -> b.issuedAt(BASE.plusSeconds(1)), b -> b.notBefore(BASE.plusSeconds(1)));
        for (var mutate : mutations) {
            String value = token(mutate);
            assertThatThrownBy(() -> decoder.decode(value)).isInstanceOf(JwtException.class);
        }
    }

    @Test void rejectsWrongKeyAndAlgorithmAndWeakOrMalformedKeys() {
        String valid = token(b -> {});
        byte[] other = new byte[64]; Arrays.fill(other, (byte) 1);
        var otherKey = new javax.crypto.spec.SecretKeySpec(other, "HmacSHA256");
        assertThatThrownBy(() -> config.jwtDecoder(otherKey, clock).decode(valid)).isInstanceOf(JwtException.class);
        var claims = claims().build();
        String hs384 = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS384).build(), claims)).getTokenValue();
        assertThatThrownBy(() -> decoder.decode(hs384)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> config.jwtSigningKey(new AuthProperties("bad!", true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.jwtSigningKey(new AuthProperties("YQ==", true))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void issuedExpiryMatchesTheActualNumericDatePrecisionAndSecretsAreRedacted() {
        var issued = new JwtTokenIssuer(encoder).issue(1, "USER", BASE.plusMillis(999));
        assertThat(issued.expiresAt()).isEqualTo(decoder.decode(issued.value()).getExpiresAt());
        assertThat(issued.expiresAt()).isEqualTo(BASE.plus(Duration.ofMinutes(15)));
        assertThat(issued.toString().contains(issued.value())).isFalse();
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder().issuer(AccessTokenValidator.ISSUER).audience(List.of(AccessTokenValidator.AUDIENCE))
                .subject("1").issuedAt(BASE).expiresAt(BASE.plus(Duration.ofMinutes(15)))
                .claim("role", "USER").claim("token_use", "access");
    }
    private String token(Consumer<JwtClaimsSet.Builder> mutation) {
        var builder = claims(); mutation.accept(builder);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }
}
