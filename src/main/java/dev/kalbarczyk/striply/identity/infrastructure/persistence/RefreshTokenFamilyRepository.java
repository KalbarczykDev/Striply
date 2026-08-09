package dev.kalbarczyk.striply.identity.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;


import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenFamilyRepository
        extends JpaRepository<RefreshTokenFamilyEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshTokenFamilyEntity> findLockedById(UUID id);

}
