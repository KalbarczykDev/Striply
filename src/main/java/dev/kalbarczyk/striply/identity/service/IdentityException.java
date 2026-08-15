package dev.kalbarczyk.striply.identity.service;

public abstract class IdentityException extends RuntimeException {
    protected IdentityException(String message) {
        super(message);
    }
}
