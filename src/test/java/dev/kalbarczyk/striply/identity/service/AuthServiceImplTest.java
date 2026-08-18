package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.exception.InvalidRegistrationException;
import dev.kalbarczyk.striply.identity.model.AppUserEntity;
import dev.kalbarczyk.striply.identity.model.RegistrationFailureReason;
import dev.kalbarczyk.striply.identity.model.UserStatus;
import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;
import dev.kalbarczyk.striply.identity.repository.AppUserRepository;
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


import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class AuthServiceImplTest {

    private static final String INPUT_EMAIL = "  Test@Example.COM ";
    private static final String NORMALIZED_EMAIL = "test@example.com";
    private static final String VALID_PASSWORD = "UnitTestPass_2026!";
    private static final String PASSWORD_ENCODER_PREFIX = "{bcrypt}";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgreSQLContainer =
            new PostgreSQLContainer("postgres:17-alpine");

    private final AuthService authService;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    AuthServiceImplTest(
            AuthService authService,
            AppUserRepository appUserRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.authService = authService;
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

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
