package com.fourlane.auth.api;

import com.fourlane.global.error.ApiException;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.nio.charset.StandardCharsets;

public final class AuthRequests {
    private AuthRequests() {}
    @Schema(name = "SignupRequest")
    public record Signup(@NotBlank @Email @Size(max = 255) String email,
                         @NotBlank @Schema(minLength = 8, maxLength = 64, description = "UTF-8 최대 72바이트") String password,
                         @NotBlank @Schema(minLength = 2, maxLength = 20) String nickname) {
        public void validate() {
            validatePassword(password);
            int length = nickname.codePointCount(0, nickname.length());
            if (length < 2 || length > 20) throw ApiException.invalidRequest("nickname", "INVALID_FORMAT");
        }
        @Override public String toString() { return "Signup[REDACTED]"; }
    }
    @Schema(name = "LoginRequest")
    public record Login(@NotBlank @Email @Size(max = 255) String email,
                        @NotBlank @Schema(minLength = 8, maxLength = 64, description = "UTF-8 최대 72바이트") String password) {
        public void validate() { validatePassword(password); }
        @Override public String toString() { return "Login[REDACTED]"; }
    }
    private static void validatePassword(String password) {
        int length = password.codePointCount(0, password.length());
        if (length < 8 || length > 64 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalidRequest("password", "INVALID_FORMAT");
        }
    }
}
