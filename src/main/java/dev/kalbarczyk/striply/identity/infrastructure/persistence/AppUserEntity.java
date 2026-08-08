package dev.kalbarczyk.striply.identity.infrastructure.persistence;

import dev.kalbarczyk.striply.identity.domain.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUserEntity {
    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @Column(name = "public_id",length = 64, unique = true, nullable = false)
    private String publicId;

    @Column(name = "email",length = 320,nullable = false)
    private String email;

    @Column(name = "normalized_email",length = 320, unique = true, nullable = false)
    private String normalizedEmail;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "status",length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at",nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at",nullable = false)
    private Instant updatedAt;


}
