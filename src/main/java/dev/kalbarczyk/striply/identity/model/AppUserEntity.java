package dev.kalbarczyk.striply.identity.model;

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

    @Column(name = "email", length = 320,unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "status", length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static AppUserEntity register(
            String email,
            String passwordHash
    ) {
        AppUserEntity user = new AppUserEntity();
        user.email = email;
        user.passwordHash = passwordHash;
        user.status = UserStatus.ACTIVE;
        return user;
    }

}
