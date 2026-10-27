package com.fourlane.auth.api;

import com.fourlane.global.security.AuthProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

@Component
public class RefreshTokenCookie {
    private final boolean secure;
    public RefreshTokenCookie(AuthProperties properties, Environment environment) {
        this.secure = environment.matchesProfiles("prod") || properties.secureCookie();
    }
    public String value(String token, long seconds) {
        return ResponseCookie.from("refreshToken", token).httpOnly(true).secure(secure)
                .sameSite("Strict").path("/api/auth").maxAge(seconds).build().toString();
    }
    public String delete() { return value("", 0); }
}
