package dev.kalbarczyk.striply.identity.application;

public enum RefreshTokenFailureReason {
    MALFORMED,
    UNKNOWN,
    EXPIRED,
    FAMILY_EXPIRED,
    FAMILY_REVOKED,
    ALREADY_CONSUMED
}
