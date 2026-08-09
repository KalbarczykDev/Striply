package dev.kalbarczyk.striply.identity.application;

import java.util.UUID;

public interface RefreshTokenService {
    IssuedRefreshToken issueFor(UUID userId);
}
