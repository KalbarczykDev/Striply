package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.exception.InvalidCredentialsException;
import dev.kalbarczyk.striply.identity.model.AppUserEntity;
import dev.kalbarczyk.striply.identity.model.UserStatus;
import dev.kalbarczyk.striply.identity.model.dto.IssuedAccessToken;
import dev.kalbarczyk.striply.identity.model.dto.IssuedRefreshToken;
import dev.kalbarczyk.striply.identity.model.dto.IssuedSession;
import dev.kalbarczyk.striply.identity.model.dto.LoginUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RotatedRefreshToken;
import dev.kalbarczyk.striply.identity.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    private static final UUID USER_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String RAW_PASSWORD = "UnitTestPass_2026!";
    private static final String PASSWORD_HASH = "{bcrypt}password-hash";
    private static final String ORIGINAL_REFRESH_TOKEN =
            "original-refresh-token";
    private static final String REPLACEMENT_REFRESH_TOKEN =
            "replacement-refresh-token";
    private static final String ACCESS_TOKEN = "access-token";
    private static final Instant ACCESS_TOKEN_EXPIRY =
            Instant.parse("2026-08-18T12:05:00Z");
    private static final Instant REFRESH_TOKEN_EXPIRY =
            Instant.parse("2026-08-25T12:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AccessTokenService accessTokenService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AppUserEntity user;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void shouldLoginActiveUserAndIssueSession() {
        IssuedAccessToken accessToken = new IssuedAccessToken(
                "access-token",
                Instant.parse("2026-08-18T12:05:00Z")
        );
        IssuedRefreshToken refreshToken = new IssuedRefreshToken(
                "refresh-token",
                Instant.parse("2026-08-25T12:00:00Z")
        );

        when(appUserRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getPasswordHash()).thenReturn(PASSWORD_HASH);
        when(user.getId()).thenReturn(USER_ID);
        when(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH))
                .thenReturn(true);
        when(accessTokenService.issue(USER_ID)).thenReturn(accessToken);
        when(refreshTokenService.issueFor(USER_ID)).thenReturn(refreshToken);

        IssuedSession session = authService.login(
                new LoginUserCommand("  Test@Example.COM ", RAW_PASSWORD)
        );

        assertThat(session).isEqualTo(
                new IssuedSession(accessToken, refreshToken)
        );
        verify(passwordEncoder).matches(RAW_PASSWORD, PASSWORD_HASH);
    }

    @Test
    void shouldRejectIncorrectPasswordWithoutIssuingTokens() {
        when(appUserRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(user));
        when(user.getStatus()).thenReturn(UserStatus.ACTIVE);
        when(user.getPasswordHash()).thenReturn(PASSWORD_HASH);
        when(passwordEncoder.matches("wrong-password", PASSWORD_HASH))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.login(
                new LoginUserCommand("test@example.com", "wrong-password")
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(accessTokenService, never()).issue(USER_ID);
        verify(refreshTokenService, never()).issueFor(USER_ID);
    }

    @Test
    void shouldRejectUnknownEmailWithInvalidCredentials() {
        when(appUserRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(
                new LoginUserCommand("missing@example.com", RAW_PASSWORD)
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(accessTokenService, never()).issue(USER_ID);
        verify(refreshTokenService, never()).issueFor(USER_ID);
    }

    @Test
    void shouldRotateRefreshTokenAndIssueAccessTokenForItsUser() {
        IssuedRefreshToken refreshToken = new IssuedRefreshToken(
                REPLACEMENT_REFRESH_TOKEN,
                REFRESH_TOKEN_EXPIRY
        );
        IssuedAccessToken accessToken = new IssuedAccessToken(
                ACCESS_TOKEN,
                ACCESS_TOKEN_EXPIRY
        );
        when(refreshTokenService.rotate(ORIGINAL_REFRESH_TOKEN))
                .thenReturn(new RotatedRefreshToken(USER_ID, refreshToken));
        when(accessTokenService.issue(USER_ID)).thenReturn(accessToken);

        IssuedSession session = authService.refresh(ORIGINAL_REFRESH_TOKEN);

        assertThat(session).isEqualTo(
                new IssuedSession(accessToken, refreshToken)
        );
    }
}
