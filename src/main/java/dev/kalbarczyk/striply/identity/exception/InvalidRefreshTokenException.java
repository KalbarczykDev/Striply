package dev.kalbarczyk.striply.identity.exception;

import dev.kalbarczyk.striply.identity.model.RefreshTokenFailureReason;
import lombok.Getter;

import java.util.Objects;

public final class InvalidRefreshTokenException extends IdentityException {
    @Getter
    private final RefreshTokenFailureReason reason;

    public InvalidRefreshTokenException(RefreshTokenFailureReason reason) {
        super("Invalid refresh token");
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }
}
