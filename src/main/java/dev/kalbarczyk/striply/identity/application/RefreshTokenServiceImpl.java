package dev.kalbarczyk.striply.identity.application;

import dev.kalbarczyk.striply.identity.domain.UserStatus;
import dev.kalbarczyk.striply.identity.infrastructure.persistence.*;
import dev.kalbarczyk.striply.identity.infrastructure.security.RefreshTokenGenerator;
import dev.kalbarczyk.striply.identity.infrastructure.security.RefreshTokenHasher;
import jakarta.transaction.Transactional;
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
                .orElseThrow(IdentityException.UserNotFoundException::new);

        if(user.getStatus() != UserStatus.ACTIVE){
            throw new IdentityException.UserNotEligibleForTokenException();
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
}
