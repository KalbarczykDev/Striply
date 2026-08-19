package dev.kalbarczyk.striply.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Configuration
public class SecurityEncodingConfiguration {
    private static final int HS256_MINIMUM_KEY_LENGTH_BYTES = 32;

    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

        return new DelegatingPasswordEncoder(
                "bcrypt",
                Map.of("bcrypt", bcrypt)
        );
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${striply.security.jwt-secret}") String secret) {
        byte[] secretBytes =
                secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < HS256_MINIMUM_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("SECRET_KEY must be at least 32 bytes long");
        }

        SecretKey secretKey = new SecretKeySpec(secretBytes, "HmacSHA256");
        return NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }
}
