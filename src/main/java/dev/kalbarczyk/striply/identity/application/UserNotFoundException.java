package dev.kalbarczyk.striply.identity.application;

public final class UserNotFoundException extends IdentityException {
    public UserNotFoundException() {
        super("User not found");
    }
}
