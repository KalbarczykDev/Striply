package dev.kalbarczyk.striply.identity.service;

public final class UserNotFoundException extends IdentityException {
    public UserNotFoundException() {
        super("User not found");
    }
}
