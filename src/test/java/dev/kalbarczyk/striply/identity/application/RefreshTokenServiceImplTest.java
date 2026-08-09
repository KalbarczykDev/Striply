package dev.kalbarczyk.striply.identity.application;

import dev.kalbarczyk.striply.identity.infrastructure.persistence.AppUserRepository;
import dev.kalbarczyk.striply.identity.domain.UserStatus;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenEntity;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenFamilyEntity;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenFamilyRepository;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenRepository;
import dev.kalbarczyk.striply.identity.infrastructure.security.RefreshTokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import dev.kalbarczyk.striply.configuration.FixedClockConfiguration;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@Import(FixedClockConfiguration.class)
class RefreshTokenServiceImplTest {


    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgreSQLContainer =
            new PostgreSQLContainer("postgres:17-alpine");


    @Autowired
    private RefreshTokenFamilyRepository familyRepository;

    @Autowired
    private RefreshTokenRepository tokenRepository;

    @Autowired
    private RefreshTokenHasher refreshTokenHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private AppUserRepository appUserRepository;

    @AfterEach
    void cleanDatabase() {
        tokenRepository.deleteAllInBatch();
        familyRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
    }

    @Test
    void shouldIssueRefreshTokenForUser() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, UserStatus.ACTIVE);

        IssuedRefreshToken issuedRefreshToken = refreshTokenService.issueFor(userId);
        List<RefreshTokenFamilyEntity> families = familyRepository.findAll();

        assertThat(families).hasSize(1);

        RefreshTokenFamilyEntity family = families.getFirst();

        assertThat(family.getUser().getId()).isEqualTo(userId);
        assertThat(family.getCreatedAt()).isEqualTo(FixedClockConfiguration.NOW);
        assertThat(family.getAbsoluteExpiresAt())
                .isEqualTo(FixedClockConfiguration.NOW.plus(Duration.ofDays(30)));
        assertThat(family.getRevokedAt()).isNull();
        assertThat(family.getRevocationReason()).isNull();

        byte[] expectedHash =
                refreshTokenHasher.hash(issuedRefreshToken.rawValue());

        RefreshTokenEntity persistedToken = tokenRepository
                .findByTokenHash(expectedHash)
                .orElseThrow();

        assertThat(persistedToken.getFamily().getId())
                .isEqualTo(family.getId());
        assertThat(persistedToken.getCreatedAt()).isEqualTo(FixedClockConfiguration.NOW);
        assertThat(persistedToken.getExpiresAt())
                .isEqualTo(FixedClockConfiguration.NOW.plus(Duration.ofDays(7)));
        assertThat(persistedToken.getConsumedAt()).isNull();

        assertThat(issuedRefreshToken.rawValue()).isNotBlank();
        assertThat(issuedRefreshToken.expiresAt())
                .isEqualTo(persistedToken.getExpiresAt());

    }

    @Test
    void shouldNotIssueRefreshTokenForDisabledUser() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, UserStatus.DISABLED);
        assertThatThrownBy(() -> refreshTokenService.issueFor(userId))
                .isInstanceOf(
                        IdentityException.UserNotEligibleForTokenException.class
                );

        assertThat(familyRepository.findAll()).isEmpty();
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRotateValidRefreshToken() {
        UUID userId = UUID.randomUUID();
        insertUser(userId, UserStatus.ACTIVE);

        IssuedRefreshToken initialToken =
                refreshTokenService.issueFor(userId);

        byte[] initialHash =
                refreshTokenHasher.hash(initialToken.rawValue());

        UUID familyId = tokenRepository
                .findFamilyIdByTokenHash(initialHash)
                .orElseThrow();

        Instant familyExpiry = familyRepository
                .findById(familyId)
                .orElseThrow()
                .getAbsoluteExpiresAt();

        IssuedRefreshToken replacement =
                refreshTokenService.rotate(initialToken.rawValue());

        assertThat(familyRepository.findAll()).hasSize(1);
        assertThat(tokenRepository.findAll()).hasSize(2);

        RefreshTokenEntity consumedOriginal = tokenRepository
                .findByTokenHash(initialHash)
                .orElseThrow();

        assertThat(consumedOriginal.getConsumedAt())
                .isEqualTo(FixedClockConfiguration.NOW);

        byte[] replacementHash =
                refreshTokenHasher.hash(replacement.rawValue());

        RefreshTokenEntity persistedReplacement = tokenRepository
                .findByTokenHash(replacementHash)
                .orElseThrow();

        UUID replacementFamilyId = tokenRepository
                .findFamilyIdByTokenHash(replacementHash)
                .orElseThrow();

        assertThat(replacementFamilyId).isEqualTo(familyId);
        assertThat(persistedReplacement.getConsumedAt()).isNull();
        assertThat(persistedReplacement.getCreatedAt())
                .isEqualTo(FixedClockConfiguration.NOW);
        assertThat(persistedReplacement.getExpiresAt())
                .isEqualTo(FixedClockConfiguration.NOW.plus(Duration.ofDays(7)));

        RefreshTokenFamilyEntity persistedFamily = familyRepository
                .findById(familyId)
                .orElseThrow();

        assertThat(persistedFamily.getAbsoluteExpiresAt())
                .isEqualTo(familyExpiry);
        assertThat(persistedFamily.getRevokedAt()).isNull();

        assertThat(replacement.expiresAt())
                .isEqualTo(persistedReplacement.getExpiresAt());
    }


    private void insertUser(UUID userId, UserStatus status) {
        jdbcTemplate.update(
                """
                        INSERT INTO app_user (
                            id,
                            public_id,
                            email,
                            normalized_email,
                            password_hash,
                            status
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                userId,
                "usr_" + UUID.randomUUID(),
                "developer@example.com",
                "developer@example.com",
                "{argon2}test-password-hash",
                status.name()
        );
    }


}
