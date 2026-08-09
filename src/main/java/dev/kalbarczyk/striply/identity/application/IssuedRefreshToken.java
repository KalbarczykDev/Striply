package dev.kalbarczyk.striply.identity.application;

import lombok.NonNull;

import java.time.Instant;

public record IssuedRefreshToken(
        String rawValue,
        Instant expiresAt
) {
    @Override
    @NonNull
    public String toString() {
        return "IssuedRefreshToken[REDACTED]";
    }
}
