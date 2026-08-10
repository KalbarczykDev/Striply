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

import static dev.kalbarczyk.striply.configuration.FixedClockConfiguration.NOW;
import static dev.kalbarczyk.striply.identity.domain.RefreshTokenRevocationReason.TOKEN_REUSE;
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
    private RefreshTokenFamilyRepository refreshTokenFamilyRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

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
        testClock.setInstant(NOW);
    }

    @AfterEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAllInBatch();
        refreshTokenFamilyRepository.deleteAllInBatch();
        appUserRepository.deleteAllInBatch();
    }

    @Test
    void shouldIssueRefreshTokenForUser() {
        UUID userId = insertUser(UserStatus.ACTIVE);

        IssuedRefreshToken issuedRefreshToken = refreshTokenService.issueFor(userId);
        List<RefreshTokenFamilyEntity> families = refreshTokenFamilyRepository.findAll();

        assertThat(families).hasSize(1);

        RefreshTokenFamilyEntity family = families.getFirst();

        assertThat(family.getUser().getId()).isEqualTo(userId);
        assertThat(family.getCreatedAt()).isEqualTo(NOW);
        assertThat(family.getAbsoluteExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(30)));
        assertThat(family.getRevokedAt()).isNull();
        assertThat(family.getRevocationReason()).isNull();

        byte[] expectedHash =
                refreshTokenHasher.hash(issuedRefreshToken.rawValue());

        RefreshTokenEntity persistedToken = refreshTokenRepository
                .findByTokenHash(expectedHash)
                .orElseThrow();

        assertThat(persistedToken.getFamily().getId())
                .isEqualTo(family.getId());
        assertThat(persistedToken.getCreatedAt()).isEqualTo(NOW);
        assertThat(persistedToken.getExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));
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

        assertThat(refreshTokenFamilyRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRotateValidRefreshToken() {
        UUID userId = insertUser(UserStatus.ACTIVE);

        IssuedRefreshToken initialToken =
                refreshTokenService.issueFor(userId);

        byte[] initialHash =
                refreshTokenHasher.hash(initialToken.rawValue());

        UUID familyId = refreshTokenRepository
                .findFamilyIdByTokenHash(initialHash)
                .orElseThrow();

        Instant familyExpiry = refreshTokenFamilyRepository
                .findById(familyId)
                .orElseThrow()
                .getAbsoluteExpiresAt();

        IssuedRefreshToken replacement =
                refreshTokenService.rotate(initialToken.rawValue());

        assertThat(refreshTokenFamilyRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        RefreshTokenEntity consumedOriginal = refreshTokenRepository
                .findByTokenHash(initialHash)
                .orElseThrow();

        assertThat(consumedOriginal.getConsumedAt())
                .isEqualTo(NOW);

        byte[] replacementHash =
                refreshTokenHasher.hash(replacement.rawValue());

        RefreshTokenEntity persistedReplacement = refreshTokenRepository
                .findByTokenHash(replacementHash)
                .orElseThrow();

        UUID replacementFamilyId = refreshTokenRepository
                .findFamilyIdByTokenHash(replacementHash)
                .orElseThrow();

        assertThat(replacementFamilyId).isEqualTo(familyId);
        assertThat(persistedReplacement.getConsumedAt()).isNull();
        assertThat(persistedReplacement.getCreatedAt())
                .isEqualTo(NOW);
        assertThat(persistedReplacement.getExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));

        RefreshTokenFamilyEntity persistedFamily = refreshTokenFamilyRepository
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

        UUID familyId = refreshTokenRepository
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

        List<RefreshTokenEntity> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);

        RefreshTokenEntity originalToken = refreshTokenRepository
                .findByTokenHash(initialHash)
                .orElseThrow();

        assertThat(originalToken.getConsumedAt()).isNull();

        RefreshTokenFamilyEntity family = refreshTokenFamilyRepository
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

        assertThat(refreshTokenRepository.findAll()).isEmpty();
        assertThat(refreshTokenFamilyRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRejectAlreadyConsumedRefreshToken() {
        UUID userId = insertUser(UserStatus.ACTIVE);

        IssuedRefreshToken first = refreshTokenService.issueFor(userId);
        refreshTokenService.rotate(first.rawValue());

        assertThatThrownBy(() -> refreshTokenService.rotate(first.rawValue()))
        .isInstanceOfSatisfying(
                InvalidRefreshTokenException.class,
                exception -> assertThat(exception.getReason())
                        .isEqualTo(RefreshTokenFailureReason.ALREADY_CONSUMED)
        );

        byte[] initialHash =
                refreshTokenHasher.hash(first.rawValue());

        UUID reloadedFamilyId = refreshTokenRepository
                .findFamilyIdByTokenHash(initialHash)
                .orElseThrow();

        RefreshTokenFamilyEntity reloadedFamily = refreshTokenFamilyRepository
                .findById(reloadedFamilyId)
                .orElseThrow();



        assertThat(reloadedFamily.getRevocationReason()).isEqualTo(TOKEN_REUSE);
        assertThat(reloadedFamily.getRevokedAt()).isEqualTo(NOW);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

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
