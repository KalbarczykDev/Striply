package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.exception.InvalidRegistrationException;
import dev.kalbarczyk.striply.identity.model.AppUserEntity;
import dev.kalbarczyk.striply.identity.model.RegistrationFailureReason;
import dev.kalbarczyk.striply.identity.model.dto.RegisterUserCommand;
import dev.kalbarczyk.striply.identity.model.dto.RegisteredUser;
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

    @Override
    @Transactional
    public RegisteredUser register(RegisterUserCommand command) {
        String password = command.password();
        validatePassword(password);
        String email = validateAndNormalizeEmail(command.email());
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

    private String validateAndNormalizeEmail(String email) {
        if (email == null) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }

        email = email.strip().toLowerCase(Locale.ROOT);

        if (email.isEmpty()) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }

        if (email.length() > EMAIL_MAX_LENGTH) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new InvalidRegistrationException(RegistrationFailureReason.INVALID_EMAIL);
        }

        return email;
    }
}
