package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.exception.InvalidCredentialsException;
import dev.kalbarczyk.striply.identity.exception.InvalidRefreshTokenException;
import dev.kalbarczyk.striply.identity.exception.InvalidRegistrationException;
import dev.kalbarczyk.striply.identity.model.AppUserEntity;
import dev.kalbarczyk.striply.identity.model.RegistrationFailureReason;
import dev.kalbarczyk.striply.identity.model.UserStatus;
import dev.kalbarczyk.striply.identity.model.dto.*;
import dev.kalbarczyk.striply.identity.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int EMAIL_MAX_LENGTH = 320;
    private static final int PASSWORD_MAX_BYTES = 72;
    private static final int PASSWORD_MIN_LENGTH = 6;
    private static final String UNIQUE_EMAIL_CONSTRAINT = "uq_app_user_email";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");


    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional
    public RegisteredUser register(RegisterUserCommand command) {
        String password = command.password();
        validatePassword(password);
        String email = normalizeEmail(command.email());
        validateEmail(email);
        String passwordHash = passwordEncoder.encode(password);

        AppUserEntity user = AppUserEntity.register(
                email,
                passwordHash
        );

        AppUserEntity savedUser = save(user);

        return new RegisteredUser(
                savedUser.getId(),
                savedUser.getEmail()
        );
    }

    @Override
    public IssuedSession login(LoginUserCommand command) {
        String email = normalizeLoginEmail(command.email());

        AppUserEntity user = appUserRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (command.password() == null
                || user.getStatus() != UserStatus.ACTIVE
                || !passwordEncoder.matches(
                command.password(),
                user.getPasswordHash()
        )) {
            throw new InvalidCredentialsException();
        }

        IssuedAccessToken accessToken = accessTokenService.issue(user.getId());
        IssuedRefreshToken refreshToken = refreshTokenService.issueFor(user.getId());

        return new IssuedSession(accessToken, refreshToken);
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedSession refresh(String refreshToken) {
        RotatedRefreshToken rotatedToken =
                refreshTokenService.rotate(refreshToken);
        IssuedAccessToken accessToken =
                accessTokenService.issue(rotatedToken.userId());

        return new IssuedSession(accessToken, rotatedToken.token());
    }

    private AppUserEntity save(AppUserEntity user) {
        try {
            return appUserRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new InvalidRegistrationException(
                        RegistrationFailureReason.EMAIL_ALREADY_EXISTS
                );
            }
            throw exception;
        }
    }

    private boolean isDuplicateEmail(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && UNIQUE_EMAIL_CONSTRAINT.equals(
                    violation.getConstraintName()
            )) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    private void validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_PASSWORD);
        }

        if (password.length() < PASSWORD_MIN_LENGTH) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_PASSWORD);
        }

        int passwordByteLength =
                password.getBytes(StandardCharsets.UTF_8).length;

        if (passwordByteLength > PASSWORD_MAX_BYTES) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_PASSWORD);
        }
    }

    private void validateEmail(String email) {
        if (email == null) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }

        if (email.isEmpty()) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }

        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }

        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String normalizeLoginEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null) {
            throw new InvalidCredentialsException();
        }

        return normalizedEmail;
    }
}
