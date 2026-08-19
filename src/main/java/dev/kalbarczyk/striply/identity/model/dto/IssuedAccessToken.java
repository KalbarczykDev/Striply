package dev.kalbarczyk.striply.identity.model.dto;

import java.time.Instant;

public record IssuedAccessToken(
        String value,
        Instant expiresAt
) {
    @Override
    @SuppressWarnings("NullableProblems")
    public String toString() {
        return "IssuedAccessToken[REDACTED]";
    }
}
