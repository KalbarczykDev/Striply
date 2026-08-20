package dev.kalbarczyk.striply.identity.exception;

public class InvalidCredentialsException extends IdentityException {
    public InvalidCredentialsException() {
        super("Invalid credentials");
    }
}
