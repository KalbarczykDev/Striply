package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.model.dto.IssuedAccessToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenServiceImplTest {

    private static final UUID USER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final Instant NOW =
            Instant.parse("2026-08-18T12:00:00Z");

    private static final SecretKey TEST_KEY = new SecretKeySpec(
            "test-signing-key-with-at-least-32-bytes"
                    .getBytes(StandardCharsets.UTF_8),
            "HmacSHA256"
    );

    private final Clock clock =
            Clock.fixed(NOW, ZoneOffset.UTC);

    private final JwtEncoder jwtEncoder =
            NimbusJwtEncoder.withSecretKey(TEST_KEY)
                    .algorithm(MacAlgorithm.HS256)
                    .build();

    private final JwtDecoder jwtDecoder = createJwtDecoder();

    private final AccessTokenService accessTokenService =
            new AccessTokenServiceImpl(jwtEncoder, clock);

    @Test
    void shouldIssueAccessToken() {
        IssuedAccessToken token = accessTokenService.issue(USER_ID);

        assertThat(token).isNotNull();
        assertThat(token.value()).isNotBlank();
        assertThat(token.expiresAt()).isNotNull();
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(300));

        Jwt jwt = jwtDecoder.decode(token.value());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("striply");
        assertThat(jwt.getSubject()).isEqualTo(USER_ID.toString());
        assertThat(jwt.getAudience()).containsExactly("striply-api");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plusSeconds(300));
    }

    private JwtDecoder createJwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(TEST_KEY)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        JwtTimestampValidator timestampValidator =
                new JwtTimestampValidator(Duration.ZERO);
        timestampValidator.setClock(clock);

        decoder.setJwtValidator(timestampValidator);

        return decoder;
    }
}
