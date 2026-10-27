package com.fourlane.global.security;

import java.time.*;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenIssuer {
    private final JwtEncoder encoder;
    public JwtTokenIssuer(JwtEncoder encoder) { this.encoder = encoder; }

    public IssuedToken issue(long memberId, String role, Instant now) {
        Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(Duration.ofMinutes(15));
        var claims = JwtClaimsSet.builder().issuer(AccessTokenValidator.ISSUER)
                .audience(java.util.List.of(AccessTokenValidator.AUDIENCE))
                .subject(Long.toString(memberId)).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("token_use", "access").claim("role", role).build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }

    public record IssuedToken(String value, Instant expiresAt) {
        @Override public String toString() { return "IssuedToken[REDACTED]"; }
    }
}
