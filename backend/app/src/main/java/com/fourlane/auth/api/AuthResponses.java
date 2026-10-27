package com.fourlane.auth.api;

import com.fourlane.auth.application.AuthResult;
import com.fourlane.member.api.MemberResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public final class AuthResponses {
    private AuthResponses() {}
    @Schema(name = "AuthResponse")
    public record Login(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant accessTokenExpiresAt,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) MemberResponse member) {
        static Login from(AuthResult result) {
            return new Login(result.accessToken(), result.accessTokenExpiresAt(), MemberResponse.from(result.member()));
        }
        @Override public String toString() { return "LoginResponse[REDACTED]"; }
    }
}
