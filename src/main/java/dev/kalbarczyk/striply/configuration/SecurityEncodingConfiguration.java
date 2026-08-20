package dev.kalbarczyk.striply.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
        SecretKey secretKey = createSecretKey(secret);
        return NimbusJwtEncoder.withSecretKey(secretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${striply.security.jwt-secret}") String secret
    ) {
        SecretKey secretKey = createSecretKey(secret);

        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerAndTimestamps =
                JwtValidators.createDefaultWithIssuer("striply");

        OAuth2TokenValidator<Jwt> audience =
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD,
                        audiences -> audiences != null
                                && audiences.contains("striply-api")
                );

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        issuerAndTimestamps,
                        audience
                )
        );

        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(resourceServer ->
                        resourceServer.jwt(Customizer.withDefaults())
                )
                .build();
    }

    private SecretKey createSecretKey(String secret) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);

        if (secretBytes.length < HS256_MINIMUM_KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                    "SECRET_KEY must be at least 32 bytes long"
            );
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }
}
