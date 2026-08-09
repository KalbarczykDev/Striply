package dev.kalbarczyk.striply.identity.application;

public class IdentityException extends RuntimeException {
    public IdentityException(String message) {
        super(message);
    }

    public static class UserNotFoundException extends IdentityException {
        public UserNotFoundException() {
            super("User not found");
        }
    }

    public static class UserNotEligibleForTokenException extends IdentityException {
        public UserNotEligibleForTokenException() {
            super("User is not eligible for Token");
        }
    }

    public static class TokenNotEligibleForRotationException extends IdentityException {
        public TokenNotEligibleForRotationException() {
            super("Token is not eligible for Rotation");
        }
    }
}
