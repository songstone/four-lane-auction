package com.fourlane.global.security;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class SecurityConfig {
    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http, SecurityErrorHandler errors) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> java.util.List.of(
                new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
        return http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(c -> c.disable()) // JSON + bearer APIs; Refresh cookie is SameSite=Strict.
                .formLogin(f -> f.disable()).httpBasic(b -> b.disable()).logout(l -> l.disable())
                .requestCache(c -> c.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/me").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/me/**", "/api/items", "/api/items/**", "/api/trades/**")
                            .hasRole("USER")
                        .requestMatchers(HttpMethod.POST, "/api/lanes/*/bids").hasRole("USER")
                        // Public reads, /ws handshake, static SPA, /error and unknown paths keep M0 behavior.
                        // Only management context exposes health; its port remains loopback-only.
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }
}
