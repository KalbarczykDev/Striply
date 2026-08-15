package dev.kalbarczyk.striply.identity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_token_family")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenFamilyEntity {
    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUserEntity user;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "absolute_expires_at", nullable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revocation_reason", length = 32)
    @Enumerated(EnumType.STRING)
    private RefreshTokenRevocationReason revocationReason;

    @Builder
    private RefreshTokenFamilyEntity(
            AppUserEntity user,
            Instant createdAt,
            Instant absoluteExpiresAt
    ) {
        this.user = Objects.requireNonNull(user);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.absoluteExpiresAt = Objects.requireNonNull(absoluteExpiresAt);

        if (!absoluteExpiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "absoluteExpiresAt must be after createdAt"
            );
        }
    }

    /**
     * Revokes token family
     *
     * @param reason    reason for revoke.
     * @param revokedAt the moment the refresh token family was revoked.
     */
    public void revoke(RefreshTokenRevocationReason reason, Instant revokedAt) {
        Objects.requireNonNull(revokedAt, "revokedAt must not be null");
        Objects.requireNonNull(reason, "reason must not be null");

        if (this.revokedAt != null) {
            return;
        }

        if (revokedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "revokedAt must not be before createdAt"
            );
        }

        this.revokedAt = revokedAt;
        this.revocationReason = reason;
    }

}
