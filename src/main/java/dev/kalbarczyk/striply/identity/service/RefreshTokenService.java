package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.model.dto.IssuedRefreshToken;

import java.util.UUID;

public interface RefreshTokenService {
    IssuedRefreshToken issueFor(UUID userId);
    IssuedRefreshToken rotate(String rawToken);
    void logout(String rawToken);
}
