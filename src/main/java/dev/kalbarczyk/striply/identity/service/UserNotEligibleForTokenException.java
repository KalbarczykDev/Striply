package dev.kalbarczyk.striply.identity.service;

public final class UserNotEligibleForTokenException
        extends IdentityException {

    public UserNotEligibleForTokenException() {
        super("User is not eligible for token issuance");
    }
}
