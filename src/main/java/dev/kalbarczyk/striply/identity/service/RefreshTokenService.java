package dev.kalbarczyk.striply.identity.service;

import java.util.UUID;

public interface RefreshTokenService {
    IssuedRefreshToken issueFor(UUID userId);
    IssuedRefreshToken rotate(String rawToken);
    void logout(String rawToken);
}
