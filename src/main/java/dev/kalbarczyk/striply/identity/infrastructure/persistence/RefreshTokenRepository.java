package dev.kalbarczyk.striply.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository
        extends JpaRepository<RefreshTokenEntity, UUID> {


    @Query(value = """
            SELECT family_id
            FROM refresh_token
            WHERE token_hash = :tokenHash
            """,
            nativeQuery = true
    )
    Optional<UUID> findFamilyIdByTokenHash(
            @Param("tokenHash") byte[] tokenHash);

    Optional<RefreshTokenEntity> findByTokenHash(byte[] tokenHash);
}
