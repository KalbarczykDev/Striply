package dev.kalbarczyk.striply.identity.application;

import dev.kalbarczyk.striply.identity.infrastructure.persistence.AppUserRepository;
import dev.kalbarczyk.striply.identity.domain.UserStatus;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenEntity;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenFamilyEntity;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenFamilyRepository;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.RefreshTokenRepository;
import dev.kalbarczyk.striply.identity.infrastructure.security.RefreshTokenHasher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    private static final UUID USER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final String USER_PUBLIC_ID =
            "usr_test_user_000000000001";

    private static final String USER_EMAIL =
            "developer@example.com";

    private static final String TEST_PASSWORD_HASH =
            "{argon2}test-password-hash";

    // 43 Base64URL characters—the shape produced from 32 random bytes.
    private static final String UNKNOWN_RAW_TOKEN =
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

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

    @Autowired
    private FixedClockConfiguration.MutableClock testClock;

    @BeforeEach
    void setUp() {
        testClock.setInstant(FixedClockConfiguration.NOW);
    }

    @AfterEach
    void cleanDatabase() {
        tokenRepository.deleteAllInBatch();
        familyRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
    }

    @Test
    void shouldIssueRefreshTokenForUser() {
        UUID userId = insertUser(UserStatus.ACTIVE);

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
        UUID userId = insertUser(UserStatus.DISABLED);
        assertThatThrownBy(() -> refreshTokenService.issueFor(userId))
                .isInstanceOf(
                        UserNotEligibleForTokenException.class
                );

        assertThat(familyRepository.findAll()).isEmpty();
        assertThat(tokenRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRotateValidRefreshToken() {
        UUID userId = insertUser(UserStatus.ACTIVE);

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

    @Test
    void shouldNotRotateExpiredRefreshToken() {
        UUID userId = insertUser(UserStatus.ACTIVE);

        IssuedRefreshToken initialToken =
                refreshTokenService.issueFor(userId);

        byte[] initialHash =
                refreshTokenHasher.hash(initialToken.rawValue());

        UUID familyId = tokenRepository
                .findFamilyIdByTokenHash(initialHash)
                .orElseThrow();

        testClock.setInstant(initialToken.expiresAt());

        assertThatThrownBy(
                () -> refreshTokenService.rotate(initialToken.rawValue())
        ).isInstanceOfSatisfying(
                InvalidRefreshTokenException.class,
                exception -> assertThat(exception.getReason())
                        .isEqualTo(RefreshTokenFailureReason.EXPIRED)
        );

        List<RefreshTokenEntity> tokens = tokenRepository.findAll();
        assertThat(tokens).hasSize(1);

        RefreshTokenEntity originalToken = tokenRepository
                .findByTokenHash(initialHash)
                .orElseThrow();

        assertThat(originalToken.getConsumedAt()).isNull();

        RefreshTokenFamilyEntity family = familyRepository
                .findById(familyId)
                .orElseThrow();

        assertThat(family.getRevokedAt()).isNull();
        assertThat(family.getRevocationReason()).isNull();


    }

    @Test
    void shouldRejectUnknownRefreshToken() {
        assertThatThrownBy(
                () -> refreshTokenService.rotate(UNKNOWN_RAW_TOKEN)
        ).isInstanceOfSatisfying(
                InvalidRefreshTokenException.class,
                exception -> assertThat(exception.getReason())
                        .isEqualTo(RefreshTokenFailureReason.UNKNOWN)
        );

        assertThat(tokenRepository.findAll()).isEmpty();
        assertThat(familyRepository.findAll()).isEmpty();
    }


    private UUID insertUser(UserStatus status) {
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
                USER_ID,
                USER_PUBLIC_ID,
                USER_EMAIL,
                USER_EMAIL,
                TEST_PASSWORD_HASH,
                status.name()
        );
        return USER_ID;
    }


}
