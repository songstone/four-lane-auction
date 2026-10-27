package com.fourlane.global.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;

/** One validation path for REST and the future M1-02 CONNECT interceptor. */
public final class AccessTokenValidator implements OAuth2TokenValidator<Jwt> {
    public static final String ISSUER = "four-lane-auction";
    public static final String AUDIENCE = "four-lane-auction-api";
    private final Clock clock;
    public AccessTokenValidator(Clock clock) { this.clock = clock; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant now = clock.instant();
        if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null) return invalid("TOKEN_INVALID");
        if (!now.isBefore(jwt.getExpiresAt())) return invalid("TOKEN_EXPIRED");
        if (jwt.getIssuedAt().isAfter(now) || (jwt.getNotBefore() != null && jwt.getNotBefore().isAfter(now))) {
            return invalid("TOKEN_INVALID");
        }
        String role = jwt.getClaimAsString("role");
        if (!ISSUER.equals(jwt.getClaimAsString("iss")) || jwt.getAudience() == null
                || !jwt.getAudience().contains(AUDIENCE)
                || !"access".equals(jwt.getClaimAsString("token_use"))
                || role == null || !Set.of("USER", "ADMIN").contains(role)) return invalid("TOKEN_INVALID");
        try {
            if (Long.parseLong(jwt.getSubject()) <= 0) return invalid("TOKEN_INVALID");
        } catch (RuntimeException invalidSubject) { return invalid("TOKEN_INVALID"); }
        return OAuth2TokenValidatorResult.success();
    }

    private OAuth2TokenValidatorResult invalid(String reason) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", reason, null));
    }
}
