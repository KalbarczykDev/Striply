package dev.kalbarczyk.striply.identity.model.dto;

import java.util.UUID;

public record RotatedRefreshToken(
        UUID userId,
        IssuedRefreshToken token
) {
}
