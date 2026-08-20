package dev.kalbarczyk.striply.identity.repository;

import dev.kalbarczyk.striply.identity.model.AppUserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository
        extends JpaRepository<AppUserEntity, UUID> {
    Optional<AppUserEntity> findByEmail(String email);
}
