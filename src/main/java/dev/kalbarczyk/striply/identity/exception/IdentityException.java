package dev.kalbarczyk.striply.identity.exception;

public abstract class IdentityException extends RuntimeException {
    protected IdentityException(String message) {
        super(message);
    }
}
