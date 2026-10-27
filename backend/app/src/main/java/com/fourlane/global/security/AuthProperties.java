package com.fourlane.global.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("fourlane.auth")
public record AuthProperties(@NotBlank String jwtSecretBase64, boolean secureCookie) {
    @Override public String toString() { return "AuthProperties[REDACTED]"; }
}
