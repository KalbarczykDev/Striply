package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.exception.InvalidRefreshTokenException;
import dev.kalbarczyk.striply.identity.exception.UserNotEligibleForTokenException;
import dev.kalbarczyk.striply.identity.model.RefreshTokenEntity;
import dev.kalbarczyk.striply.identity.model.RefreshTokenFailureReason;
import dev.kalbarczyk.striply.identity.model.RefreshTokenFamilyEntity;
import dev.kalbarczyk.striply.identity.model.UserStatus;
import dev.kalbarczyk.striply.identity.model.dto.IssuedRefreshToken;
import dev.kalbarczyk.striply.identity.repository.AppUserRepository;
import dev.kalbarczyk.striply.identity.repository.RefreshTokenFamilyRepository;
import dev.kalbarczyk.striply.identity.repository.RefreshTokenRepository;
import dev.kalbarczyk.striply.identity.security.RefreshTokenHasher;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static dev.kalbarczyk.striply.configuration.FixedClockConfiguration.NOW;
import static dev.kalbarczyk.striply.identity.model.RefreshTokenRevocationReason.*;
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
        TokenFixture initial = issueTokenForActiveUser();

        Instant familyExpiry = refreshTokenFamilyRepository
                .findById(initial.familyId())
                .orElseThrow()
                .getAbsoluteExpiresAt();

        IssuedRefreshToken replacement =
                refreshTokenService.rotate(initial.issued().rawValue());

        assertThat(refreshTokenFamilyRepository.findAll()).hasSize(1);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

        RefreshTokenEntity consumedOriginal = refreshTokenRepository
                .findByTokenHash(initial.hash())
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

        assertThat(replacementFamilyId).isEqualTo(initial.familyId());
        assertThat(persistedReplacement.getConsumedAt()).isNull();
        assertThat(persistedReplacement.getCreatedAt())
                .isEqualTo(NOW);
        assertThat(persistedReplacement.getExpiresAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));

        RefreshTokenFamilyEntity persistedFamily = refreshTokenFamilyRepository
                .findById(initial.familyId())
                .orElseThrow();

        assertThat(persistedFamily.getAbsoluteExpiresAt())
                .isEqualTo(familyExpiry);
        assertThat(persistedFamily.getRevokedAt()).isNull();

        assertThat(replacement.expiresAt())
                .isEqualTo(persistedReplacement.getExpiresAt());
    }

    @Test
    void shouldNotRotateExpiredRefreshToken() {
        TokenFixture initial = issueTokenForActiveUser();

        testClock.setInstant(initial.issued().expiresAt());

        assertThatThrownBy(
                () -> refreshTokenService.rotate(initial.issued().rawValue())
        ).isInstanceOfSatisfying(
                InvalidRefreshTokenException.class,
                exception -> assertThat(exception.getReason())
                        .isEqualTo(RefreshTokenFailureReason.EXPIRED)
        );

        List<RefreshTokenEntity> tokens = refreshTokenRepository.findAll();
        assertThat(tokens).hasSize(1);

        RefreshTokenEntity originalToken = refreshTokenRepository
                .findByTokenHash(initial.hash())
                .orElseThrow();

        assertThat(originalToken.getConsumedAt()).isNull();

        RefreshTokenFamilyEntity family = refreshTokenFamilyRepository
                .findById(initial.familyId())
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
        TokenFixture first = issueTokenForActiveUser();
        refreshTokenService.rotate(first.issued().rawValue());

        assertThatThrownBy(
                () -> refreshTokenService.rotate(first.issued().rawValue())
        )
                .isInstanceOfSatisfying(
                        InvalidRefreshTokenException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(RefreshTokenFailureReason.ALREADY_CONSUMED)
                );

        RefreshTokenFamilyEntity reloadedFamily = refreshTokenFamilyRepository
                .findById(first.familyId())
                .orElseThrow();


        assertThat(reloadedFamily.getRevocationReason()).isEqualTo(TOKEN_REUSE);
        assertThat(reloadedFamily.getRevokedAt()).isEqualTo(NOW);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

    }

    @Test
    void shouldTreatExpiredConsumedTokenAsReplay() {
        TokenFixture original = issueTokenForActiveUser();
        refreshTokenService.rotate(original.issued().rawValue());

        testClock.setInstant(
                NOW.plus(Duration.ofDays(7))
        );

        assertThatThrownBy(
                () -> refreshTokenService.rotate(original.issued().rawValue())
        )
                .isInstanceOfSatisfying(
                        InvalidRefreshTokenException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(RefreshTokenFailureReason.ALREADY_CONSUMED)
                );

        RefreshTokenFamilyEntity family = refreshTokenFamilyRepository
                .findById(original.familyId())
                .orElseThrow();

        assertThat(family.getRevocationReason()).isEqualTo(TOKEN_REUSE);
        assertThat(family.getRevokedAt())
                .isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(refreshTokenRepository.findAll()).hasSize(2);

    }

    @Test
    void shouldRejectRefreshTokenFromRevokedFamily() {
        TokenFixture original = issueTokenForActiveUser();

        RefreshTokenFamilyEntity family = refreshTokenFamilyRepository
                .findById(original.familyId())
                .orElseThrow();

        family.revoke(SECURITY_ACTION, NOW);
        refreshTokenFamilyRepository.save(family);

        assertThatThrownBy(
                () -> refreshTokenService.rotate(original.issued().rawValue())
        )
                .isInstanceOfSatisfying(
                        InvalidRefreshTokenException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(RefreshTokenFailureReason.FAMILY_REVOKED)
                );

        RefreshTokenEntity reloadedToken = refreshTokenRepository
                .findByTokenHash(original.hash())
                .orElseThrow();

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
        assertThat(reloadedToken.getConsumedAt()).isNull();

    }

    @Test
    void shouldRejectRefreshTokenWhenFamilyHasExpired() {
        TokenFixture original = issueTokenForActiveUser();

        testClock.setInstant(NOW.plus(Duration.ofDays(30)));

        assertThatThrownBy(
                () -> refreshTokenService.rotate(original.issued().rawValue())
        )
                .isInstanceOfSatisfying(
                        InvalidRefreshTokenException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(RefreshTokenFailureReason.FAMILY_EXPIRED)
                );

        RefreshTokenEntity persistedOriginal = refreshTokenRepository
                .findByTokenHash(original.hash())
                .orElseThrow();
        RefreshTokenFamilyEntity persistedFamily = refreshTokenFamilyRepository
                .findById(original.familyId())
                .orElseThrow();

        assertThat(refreshTokenRepository.findAll()).hasSize(1);
        assertThat(persistedOriginal.getConsumedAt()).isNull();
        assertThat(persistedFamily.getRevokedAt()).isNull();
        assertThat(persistedFamily.getRevocationReason()).isNull();
    }

    @Test
    void shouldAllowOnlyOneConcurrentRotationAndRevokeFamilyAsReplay() throws Exception {
        TokenFixture original = issueTokenForActiveUser();

        CountDownLatch requestsReady = new CountDownLatch(2);
        CountDownLatch startRequests = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            try {
                var rotation = (java.util.concurrent.Callable<Object>) () -> {
                    requestsReady.countDown();
                    startRequests.await();

                    try {
                        return refreshTokenService.rotate(
                                original.issued().rawValue()
                        );
                    } catch (InvalidRefreshTokenException exception) {
                        return exception;
                    }
                };

                Future<Object> firstResult = executor.submit(rotation);
                Future<Object> secondResult = executor.submit(rotation);

                assertThat(requestsReady.await(5, TimeUnit.SECONDS)).isTrue();
                startRequests.countDown();

                List<Object> results = List.of(
                        firstResult.get(10, TimeUnit.SECONDS),
                        secondResult.get(10, TimeUnit.SECONDS)
                );

                assertThat(results)
                        .filteredOn(IssuedRefreshToken.class::isInstance)
                        .hasSize(1);
                assertThat(results)
                        .filteredOn(InvalidRefreshTokenException.class::isInstance)
                        .singleElement()
                        .satisfies(result -> assertThat(
                                ((InvalidRefreshTokenException) result).getReason()
                        ).isEqualTo(RefreshTokenFailureReason.ALREADY_CONSUMED));
            } finally {
                startRequests.countDown();
                executor.shutdownNow();
            }
        }

        RefreshTokenFamilyEntity persistedFamily = refreshTokenFamilyRepository
                .findById(original.familyId())
                .orElseThrow();

        assertThat(persistedFamily.getRevokedAt()).isEqualTo(NOW);
        assertThat(persistedFamily.getRevocationReason()).isEqualTo(TOKEN_REUSE);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);
    }

    @Test
    void shouldRevokeRefreshTokenFamilyOnLogout() {
        TokenFixture original = issueTokenForActiveUser();

        refreshTokenService.logout(original.issued().rawValue());

        RefreshTokenFamilyEntity family = refreshTokenFamilyRepository
                .findById(original.familyId())
                .orElseThrow();

        RefreshTokenEntity persistedToken = refreshTokenRepository
                .findByTokenHash(original.hash())
                .orElseThrow();

        assertThat(family.getRevokedAt()).isEqualTo(NOW);
        assertThat(family.getRevocationReason()).isEqualTo(LOGOUT);
        assertThat(persistedToken.getConsumedAt()).isNull();
        assertThat(refreshTokenRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldTreatLogoutWithConsumedTokenAsReplay() {
        TokenFixture original = issueTokenForActiveUser();

        IssuedRefreshToken replacement =
                refreshTokenService.rotate(original.issued().rawValue());
        byte[] replacementHash =
                refreshTokenHasher.hash(replacement.rawValue());

        refreshTokenService.logout(original.issued().rawValue());

        assertThat(refreshTokenFamilyRepository.findById(original.familyId()))
                .hasValueSatisfying(family -> {
                    assertThat(family.getRevokedAt()).isEqualTo(NOW);
                    assertThat(family.getRevocationReason())
                            .isEqualTo(TOKEN_REUSE);
                });

        assertThat(refreshTokenRepository.findByTokenHash(original.hash()))
                .hasValueSatisfying(token ->
                        assertThat(token.getConsumedAt()).isEqualTo(NOW));

        assertThat(refreshTokenRepository.findByTokenHash(replacementHash))
                .hasValueSatisfying(token ->
                        assertThat(token.getConsumedAt()).isNull());

        assertThat(refreshTokenRepository.findAll()).hasSize(2);
    }

    @Test
    void shouldTreatUnknownTokenLogoutAsSuccessfulNoOp(){
        refreshTokenService.logout(UNKNOWN_RAW_TOKEN);

        assertThat(refreshTokenFamilyRepository.findAll()).isEmpty();
        assertThat(refreshTokenRepository.findAll()).isEmpty();
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

    private TokenFixture issueTokenForActiveUser() {
        UUID userId = insertUser(UserStatus.ACTIVE);
        IssuedRefreshToken issued = refreshTokenService.issueFor(userId);
        byte[] hash = refreshTokenHasher.hash(issued.rawValue());
        UUID familyId = refreshTokenRepository
                .findFamilyIdByTokenHash(hash)
                .orElseThrow();

        return new TokenFixture(issued, hash, familyId);
    }

    private record TokenFixture(
            IssuedRefreshToken issued,
            byte[] hash,
            UUID familyId
    ) {
    }
}
