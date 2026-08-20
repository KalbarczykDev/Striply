package dev.kalbarczyk.striply.identity.model.dto;

public record IssuedSession(
        IssuedAccessToken accessToken,
        IssuedRefreshToken refreshToken
) {
}
