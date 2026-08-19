package dev.kalbarczyk.striply.identity.service;

import dev.kalbarczyk.striply.identity.model.dto.IssuedAccessToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessTokenServiceImpl implements AccessTokenService {
    private static final Duration ACCESS_TOKEN_LIFETIME = Duration.ofMinutes(5);
    private final JwtEncoder jwtEncoder;
    private final Clock clock;

    @Override
    public IssuedAccessToken issue(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ACCESS_TOKEN_LIFETIME);

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("striply")
                .subject(userId.toString())
                .audience(List.of("striply-api"))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();

        Jwt encodedJwt = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        );

        return new IssuedAccessToken(
                encodedJwt.getTokenValue(),
                expiresAt
        );
    }
}
