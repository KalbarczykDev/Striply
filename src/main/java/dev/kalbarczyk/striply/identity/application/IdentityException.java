package dev.kalbarczyk.striply.identity.application;

public abstract class IdentityException extends RuntimeException {
    protected IdentityException(String message) {
        super(message);
    }
}
