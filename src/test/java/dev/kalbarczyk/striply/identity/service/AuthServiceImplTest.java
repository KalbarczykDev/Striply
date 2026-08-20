package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.exception.InvalidCredentialsException;
import dev.kalbarczyk.striply.identity.exception.InvalidRegistrationException;
import dev.kalbarczyk.striply.identity.model.AppUserEntity;
import dev.kalbarczyk.striply.identity.model.RegistrationFailureReason;
import dev.kalbarczyk.striply.identity.model.UserStatus;
import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;
import dev.kalbarczyk.striply.identity.model.dto.IssuedAccessToken;
import dev.kalbarczyk.striply.identity.model.dto.IssuedRefreshToken;
import dev.kalbarczyk.striply.identity.model.dto.IssuedSession;
import dev.kalbarczyk.striply.identity.model.dto.LoginUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RotatedRefreshToken;
import dev.kalbarczyk.striply.identity.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;


import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class AuthServiceImplTest {

    private static final String INPUT_EMAIL = "  Test@Example.COM ";
    private static final String NORMALIZED_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "UnitTestPass_2026!";
    private static final String PASSWORD_ENCODER_PREFIX = "{bcrypt}";
    private static final UUID USER_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final String PASSWORD_HASH = "{bcrypt}password-hash";
    private static final String WRONG_PASSWORD = "wrong-password";
    private static final String MISSING_EMAIL = "missing@example.com";
    private static final String ORIGINAL_REFRESH_TOKEN =
            "original-refresh-token";
    private static final String REPLACEMENT_REFRESH_TOKEN =
            "replacement-refresh-token";
    private static final String ACCESS_TOKEN = "access-token";
    private static final Instant ACCESS_TOKEN_EXPIRY =
            Instant.parse("2026-08-18T12:05:00Z");
    private static final Instant REFRESH_TOKEN_EXPIRY =
            Instant.parse("2026-08-25T12:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgreSQLContainer =
            new PostgreSQLContainer("postgres:17-alpine");

    private final AuthService authService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    @AfterEach
    void cleanDatabase() {
        appUserRepository.deleteAllInBatch();
    }

    @Test
    void shouldRegisterUser() {
        RegisterUserCommand command = new RegisterUserCommand(
                INPUT_EMAIL,
                VALID_PASSWORD
        );

        RegisteredUser registeredUser = authService.register(command);

        AppUserEntity createdUser = appUserRepository.findById((registeredUser.id())).orElseThrow();

        assertThat(registeredUser.email()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(createdUser.getEmail()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(createdUser.getStatus()).isEqualTo(UserStatus.ACTIVE);

        assertThat(createdUser.getPasswordHash())
                .isNotEqualTo(VALID_PASSWORD);
        assertThat(createdUser.getPasswordHash()).startsWith(PASSWORD_ENCODER_PREFIX);

        assertThat(passwordEncoder.matches(
                VALID_PASSWORD,
                createdUser.getPasswordHash()
        )).isTrue();
    }

    @ParameterizedTest
    @MethodSource("passwordsExceedingBcryptLimit")
    void shouldRejectPasswordExceedingBcryptLimit(String password) {
        assertRegistrationFailure(
                new RegisterUserCommand(INPUT_EMAIL, password),
                RegistrationFailureReason.INVALID_PASSWORD
        );
    }

    @ParameterizedTest
    @MethodSource("invalidEmails")
    void shouldRejectInvalidEmail(String email) {
        assertRegistrationFailure(
                new RegisterUserCommand(email, VALID_PASSWORD),
                RegistrationFailureReason.INVALID_EMAIL
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPasswords")
    void shouldRejectInvalidPassword(String password) {
        assertRegistrationFailure(
                new RegisterUserCommand(INPUT_EMAIL, password),
                RegistrationFailureReason.INVALID_PASSWORD
        );
    }

    @Test
    void shouldRejectDuplicateNormalizedEmail() {
        authService.register(new RegisterUserCommand(
                "Test@Example.com",
                VALID_PASSWORD
        ));

        assertRegistrationFailure(
                new RegisterUserCommand(
                        " test@example.COM ",
                        VALID_PASSWORD
                ),
                RegistrationFailureReason.EMAIL_ALREADY_EXISTS
        );

        assertThat(appUserRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldLoginActiveUserAndIssueSession() {
        AuthFixture fixture = authFixture();
        IssuedAccessToken accessToken = issuedAccessToken();
        IssuedRefreshToken refreshToken = issuedRefreshToken();

        when(fixture.appUserRepository().findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(fixture.user()));
        when(fixture.user().getStatus()).thenReturn(UserStatus.ACTIVE);
        when(fixture.user().getPasswordHash()).thenReturn(PASSWORD_HASH);
        when(fixture.user().getId()).thenReturn(USER_ID);
        when(fixture.passwordEncoder().matches(
                VALID_PASSWORD,
                PASSWORD_HASH
        )).thenReturn(true);
        when(fixture.accessTokenService().issue(USER_ID))
                .thenReturn(accessToken);
        when(fixture.refreshTokenService().issueFor(USER_ID))
                .thenReturn(refreshToken);

        IssuedSession session = fixture.authService().login(
                new LoginUserCommand(INPUT_EMAIL, VALID_PASSWORD)
        );

        assertThat(session).isEqualTo(
                new IssuedSession(accessToken, refreshToken)
        );
        verify(fixture.passwordEncoder()).matches(
                VALID_PASSWORD,
                PASSWORD_HASH
        );
    }

    @Test
    void shouldRejectIncorrectPasswordWithoutIssuingTokens() {
        AuthFixture fixture = authFixture();
        when(fixture.appUserRepository().findByEmail(NORMALIZED_EMAIL))
                .thenReturn(Optional.of(fixture.user()));
        when(fixture.user().getStatus()).thenReturn(UserStatus.ACTIVE);
        when(fixture.user().getPasswordHash()).thenReturn(PASSWORD_HASH);
        when(fixture.passwordEncoder().matches(
                WRONG_PASSWORD,
                PASSWORD_HASH
        )).thenReturn(false);

        assertThatThrownBy(() -> fixture.authService().login(
                new LoginUserCommand(NORMALIZED_EMAIL, WRONG_PASSWORD)
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(fixture.accessTokenService(), never()).issue(USER_ID);
        verify(fixture.refreshTokenService(), never()).issueFor(USER_ID);
    }

    @Test
    void shouldRejectUnknownEmailWithInvalidCredentials() {
        AuthFixture fixture = authFixture();
        when(fixture.appUserRepository().findByEmail(MISSING_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.authService().login(
                new LoginUserCommand(MISSING_EMAIL, VALID_PASSWORD)
        )).isInstanceOf(InvalidCredentialsException.class);

        verify(fixture.accessTokenService(), never()).issue(USER_ID);
        verify(fixture.refreshTokenService(), never()).issueFor(USER_ID);
    }

    @Test
    void shouldRotateRefreshTokenAndIssueAccessTokenForItsUser() {
        AuthFixture fixture = authFixture();
        IssuedRefreshToken refreshToken = issuedRefreshToken();
        IssuedAccessToken accessToken = issuedAccessToken();
        when(fixture.refreshTokenService().rotate(ORIGINAL_REFRESH_TOKEN))
                .thenReturn(new RotatedRefreshToken(USER_ID, refreshToken));
        when(fixture.accessTokenService().issue(USER_ID))
                .thenReturn(accessToken);

        IssuedSession session = fixture.authService().refresh(
                ORIGINAL_REFRESH_TOKEN
        );

        assertThat(session).isEqualTo(
                new IssuedSession(accessToken, refreshToken)
        );
    }

    @Test
    void shouldDelegateLogoutToRefreshTokenService() {
        AuthFixture fixture = authFixture();

        fixture.authService().logout(ORIGINAL_REFRESH_TOKEN);

        verify(fixture.refreshTokenService()).logout(ORIGINAL_REFRESH_TOKEN);
    }

    private void assertRegistrationFailure(
            RegisterUserCommand command,
            RegistrationFailureReason expectedReason
    ) {

        assertThatThrownBy(() -> authService.register(command))
                .isInstanceOfSatisfying(
                        InvalidRegistrationException.class,
                        exception -> assertThat(exception.getReason())
                                .isEqualTo(
                                        expectedReason
                                )
                );
    }

    private AuthFixture authFixture() {
        AppUserRepository repository = mock(AppUserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AccessTokenService accessTokens = mock(AccessTokenService.class);
        RefreshTokenService refreshTokens = mock(RefreshTokenService.class);

        return new AuthFixture(
                new AuthServiceImpl(
                        repository,
                        encoder,
                        accessTokens,
                        refreshTokens
                ),
                repository,
                encoder,
                accessTokens,
                refreshTokens,
                mock(AppUserEntity.class)
        );
    }

    private IssuedAccessToken issuedAccessToken() {
        return new IssuedAccessToken(ACCESS_TOKEN, ACCESS_TOKEN_EXPIRY);
    }

    private IssuedRefreshToken issuedRefreshToken() {
        return new IssuedRefreshToken(
                REPLACEMENT_REFRESH_TOKEN,
                REFRESH_TOKEN_EXPIRY
        );
    }

    private record AuthFixture(
            AuthServiceImpl authService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService,
            AppUserEntity user
    ) {
    }

    static Stream<String> passwordsExceedingBcryptLimit() {
        return Stream.of(
                "a".repeat(73),
                "ą".repeat(37)
        );
    }

    static Stream<String> invalidEmails() {
        return Stream.of(
                null,
                "",
                "   ",
                "invalid-email",
                "a".repeat(309) + "@example.com"
        );
    }

    static Stream<String> invalidPasswords() {
        return Stream.of(
                null,
                "",
                "a".repeat(5)
        );
    }
}
