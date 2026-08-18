package dev.kalbarczyk.striply.identity.exception;

import dev.kalbarczyk.striply.identity.model.RegistrationFailureReason;
import lombok.Getter;

import java.util.Objects;

public final class InvalidRegistrationException extends IdentityException {
    @Getter
    private final RegistrationFailureReason reason;

    public InvalidRegistrationException(
            RegistrationFailureReason reason
    ) {
        super("Registration rejected");
        this.reason = Objects.requireNonNull(
                reason,
                "reason must not be null"
        );
    }
}
