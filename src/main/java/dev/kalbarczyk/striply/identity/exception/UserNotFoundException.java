package dev.kalbarczyk.striply.identity.exception;

public final class UserNotFoundException extends IdentityException {
    public UserNotFoundException() {
        super("User not found");
    }
}
