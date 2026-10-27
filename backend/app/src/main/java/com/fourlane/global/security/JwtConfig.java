package com.fourlane.global.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
public class JwtConfig {
    @Bean
    SecretKey jwtSigningKey(AuthProperties properties) {
        byte[] decoded;
        try { decoded = Base64.getDecoder().decode(properties.jwtSecretBase64()); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("JWT key must be Base64"); }
        if (decoded.length < 32) throw new IllegalArgumentException("JWT key must contain at least 256 bits");
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // Nimbus signature verification delegates claims to this Clock-based validator.
        decoder.setJwtValidator(new AccessTokenValidator(clock));
        return decoder;
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
    @Bean PasswordEncoder passwordEncoder() { return PasswordEncoderFactories.createDelegatingPasswordEncoder(); }
}
