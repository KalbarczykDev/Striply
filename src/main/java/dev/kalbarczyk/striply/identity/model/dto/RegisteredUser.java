package dev.kalbarczyk.striply.identity.model.dto;

import java.util.UUID;

public record RegisteredUser(
        UUID id,
        String email
) {
}
