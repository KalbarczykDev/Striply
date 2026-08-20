package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.exception.InvalidRefreshTokenException;
import dev.kalbarczyk.striply.identity.exception.UserNotEligibleForTokenException;
import dev.kalbarczyk.striply.identity.exception.UserNotFoundException;
import dev.kalbarczyk.striply.identity.model.*;
import dev.kalbarczyk.striply.identity.model.dto.IssuedRefreshToken;
import dev.kalbarczyk.striply.identity.model.dto.RotatedRefreshToken;
import dev.kalbarczyk.striply.identity.repository.AppUserRepository;
import dev.kalbarczyk.striply.identity.repository.RefreshTokenFamilyRepository;
import dev.kalbarczyk.striply.identity.repository.RefreshTokenRepository;
import dev.kalbarczyk.striply.identity.security.RefreshTokenGenerator;
import dev.kalbarczyk.striply.identity.security.RefreshTokenHasher;
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
    public RotatedRefreshToken rotate(String rawToken) {

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

        if (!now.isBefore(family.getAbsoluteExpiresAt())) {
            throw new InvalidRefreshTokenException(
                    RefreshTokenFailureReason.FAMILY_EXPIRED
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


        return new RotatedRefreshToken(
                family.getUser().getId(),
                new IssuedRefreshToken(
                        replacementRawToken,
                        replacementExpiry
                )
        );
    }

    @Override
    @Transactional
    public void logout(String rawToken) {
        byte[] tokenHash = refreshTokenHasher.hash(rawToken);

        refreshTokenRepository.findFamilyIdByTokenHash(tokenHash)
                .flatMap(refreshTokenFamilyRepository::findLockedById)
                .ifPresent(family -> revokeForLogout(family, tokenHash));
    }

    private void revokeForLogout(
            RefreshTokenFamilyEntity family,
            byte[] tokenHash
    ) {
        refreshTokenRepository.findByTokenHash(tokenHash)
                .ifPresent(token -> {
                    RefreshTokenRevocationReason reason =
                            token.getConsumedAt() == null
                                    ? RefreshTokenRevocationReason.LOGOUT
                                    : RefreshTokenRevocationReason.TOKEN_REUSE;
                    family.revoke(reason, clock.instant());
                });
    }
}
