package com.fourlane.auth.application;

import com.fourlane.member.application.MemberView;
import java.time.Instant;

public record AuthResult(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                         long refreshMaxAgeSeconds, MemberView member) {
    @Override public String toString() { return "AuthResult[REDACTED]"; }
}
