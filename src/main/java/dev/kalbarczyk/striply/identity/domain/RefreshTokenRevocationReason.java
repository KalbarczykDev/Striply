package dev.kalbarczyk.striply.identity.domain;

public enum RefreshTokenRevocationReason {
    LOGOUT,
    TOKEN_REUSE,
    SECURITY_ACTION
}
