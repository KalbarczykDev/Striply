package dev.kalbarczyk.striply.identity.model.dto;

public record LoginUserCommand(
        String email,
        String password
) {
}
