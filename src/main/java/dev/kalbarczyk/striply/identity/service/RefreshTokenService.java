package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.model.dto.IssuedRefreshToken;
import dev.kalbarczyk.striply.identity.model.dto.RotatedRefreshToken;

import java.util.UUID;

public interface RefreshTokenService {
    IssuedRefreshToken issueFor(UUID userId);
    RotatedRefreshToken rotate(String rawToken);
    void logout(String rawToken);
}
