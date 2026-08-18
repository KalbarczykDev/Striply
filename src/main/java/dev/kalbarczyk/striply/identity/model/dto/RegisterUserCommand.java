package dev.kalbarczyk.striply.identity.model.dto;

public record RegisterUserCommand(
        String email,
        String password
) {
}
