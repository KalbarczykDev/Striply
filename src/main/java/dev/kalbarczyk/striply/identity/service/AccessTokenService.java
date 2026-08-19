package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.model.dto.IssuedAccessToken;

import java.util.UUID;

public interface AccessTokenService {
    IssuedAccessToken issue(UUID userId);
}
