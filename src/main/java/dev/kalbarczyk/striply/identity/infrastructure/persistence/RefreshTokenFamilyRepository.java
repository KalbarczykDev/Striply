package dev.kalbarczyk.striply.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenFamilyRepository
        extends JpaRepository<RefreshTokenFamilyEntity, UUID> {

    @Query(
            value = """
                    SELECT *
                    FROM refresh_token_family
                    WHERE id = :familyId
                    FOR UPDATE
                    """,
            nativeQuery = true
    )
    Optional<RefreshTokenFamilyEntity> findByIdForUpdate(
            @Param("familyId") UUID familyId
    );

}
