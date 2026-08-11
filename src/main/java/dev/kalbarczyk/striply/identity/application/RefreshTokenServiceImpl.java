package dev.kalbarczyk.striply.identity.application;

import dev.kalbarczyk.striply.identity.domain.RefreshTokenRevocationReason;
import dev.kalbarczyk.striply.identity.domain.UserStatus;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.*;
import dev.kalbarczyk.striply.identity.infrastructure.security.RefreshTokenGenerator;
import dev.kalbarczyk.striply.identity.infrastructure.security.RefreshTokenHasher;
import org.springframework.transaction.annotation.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@AllArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Duration TOKEN_LIFETIME = Duration.ofDays(7);
    private static final Duration FAMILY_LIFETIME = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenFamilyRepository refreshTokenFamilyRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenHasher refreshTokenHasher;
    private final AppUserRepository appUserRepository;

    private final Clock clock;

    @Override
    @Transactional
    public IssuedRefreshToken issueFor(UUID userId) {
        AppUserEntity user = appUserRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new UserNotEligibleForTokenException();
        }

        Instant now = clock.instant();

        RefreshTokenFamilyEntity family = RefreshTokenFamilyEntity.builder()
                .user(user)
                .createdAt(now)
                .absoluteExpiresAt(now.plus(FAMILY_LIFETIME))
                .build();

        refreshTokenFamilyRepository.save(family);

        String rawToken = refreshTokenGenerator.generate();

        byte[] hashedToken = refreshTokenHasher.hash(rawToken);

        RefreshTokenEntity refreshToken = RefreshTokenEntity.builder()
                .tokenHash(hashedToken)
                .family(family)
                .createdAt(now)
                .expiresAt(now.plus(TOKEN_LIFETIME))
                .build();

        refreshTokenRepository.save(refreshToken);

        return new IssuedRefreshToken(rawToken, refreshToken.getExpiresAt());
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedRefreshToken rotate(String rawToken) {

        byte[] presentedTokenHash = refreshTokenHasher.hash(rawToken);

        UUID familyId = refreshTokenRepository
                .findFamilyIdByTokenHash(presentedTokenHash)
                .orElseThrow(
                        () -> new InvalidRefreshTokenException(
                                RefreshTokenFailureReason.UNKNOWN
                        )
                );

        RefreshTokenFamilyEntity family = refreshTokenFamilyRepository
                .findLockedById(familyId)
                .orElseThrow(
                        () -> new InvalidRefreshTokenException(
                                RefreshTokenFailureReason.UNKNOWN
                        )
                );

        RefreshTokenEntity presentedToken = refreshTokenRepository
                .findByTokenHash(presentedTokenHash)
                .orElseThrow(
                        () -> new InvalidRefreshTokenException(
                                RefreshTokenFailureReason.UNKNOWN
                        )
                );

        Instant now = clock.instant();

        if (presentedToken.getConsumedAt() != null) {
            family.revoke(RefreshTokenRevocationReason.TOKEN_REUSE, now);
            throw new InvalidRefreshTokenException(
                    RefreshTokenFailureReason.ALREADY_CONSUMED
            );
        }

        if (family.getRevokedAt() != null) {
            throw new InvalidRefreshTokenException(
                    RefreshTokenFailureReason.FAMILY_REVOKED
            );
        }

        if (!now.isBefore(presentedToken.getExpiresAt())) {
            throw new InvalidRefreshTokenException(
                    RefreshTokenFailureReason.EXPIRED
            );
        }

        presentedToken.consume(now);

        /*
         * Force Hibernate to update consumed_at before inserting the replacement.
         * Otherwise, PostgreSQL's partial unique index could see two unconsumed
         * tokens in the same family.
         */
        refreshTokenRepository.flush();

        String replacementRawToken = refreshTokenGenerator.generate();
        byte[] replacementTokenHash =
                refreshTokenHasher.hash(replacementRawToken);

        Instant normalExpiry = now.plus(TOKEN_LIFETIME);
        Instant replacementExpiry =
                normalExpiry.isBefore(family.getAbsoluteExpiresAt())
                        ? normalExpiry
                        : family.getAbsoluteExpiresAt();

        RefreshTokenEntity replacementToken =
                RefreshTokenEntity.builder()
                        .family(family)
                        .tokenHash(replacementTokenHash)
                        .createdAt(now)
                        .expiresAt(replacementExpiry)
                        .build();

        refreshTokenRepository.save(replacementToken);


        return new IssuedRefreshToken(
                replacementRawToken,
                replacementExpiry
        );
    }
}
