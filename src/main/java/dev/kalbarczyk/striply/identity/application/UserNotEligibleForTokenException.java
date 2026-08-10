package dev.kalbarczyk.striply.identity.application;

public final class UserNotEligibleForTokenException
        extends IdentityException {

    public UserNotEligibleForTokenException() {
        super("User is not eligible for token issuance");
    }
}
