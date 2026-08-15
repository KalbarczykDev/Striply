package dev.kalbarczyk.striply.identity.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity {
    @Id
    @UuidGenerator
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "family_id", nullable = false)
    private RefreshTokenFamilyEntity family;

    @Column(name = "token_hash", nullable = false, unique = true)
    @Getter(AccessLevel.NONE)
    private byte[] tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Builder
    private RefreshTokenEntity(RefreshTokenFamilyEntity family, byte[] tokenHash, Instant createdAt, Instant expiresAt) {
        this.family = Objects.requireNonNull(
                family,
                "family must not be null"
        );
        this.createdAt = Objects.requireNonNull(
                createdAt,
                "createdAt must not be null"
        );
        this.expiresAt = Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );

        Objects.requireNonNull(tokenHash, "tokenHash must not be null");

        if (tokenHash.length != 32) {
            throw new IllegalArgumentException(
                    "tokenHash must contain exactly 32 bytes"
            );
        }

        if (!expiresAt.isAfter(createdAt)) {
            throw new IllegalArgumentException(
                    "expiresAt must be after createdAt"
            );
        }

        if (expiresAt.isAfter(family.getAbsoluteExpiresAt())) {
            throw new IllegalArgumentException(
                    "expiresAt must not exceed the family expiry"
            );
        }

        if (createdAt.isBefore(family.getCreatedAt())) {
            throw new IllegalArgumentException(
                    "createdAt must not be before the family creation time"
            );
        }

        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
    }


    /**
     * Consumes token
     *
     * @param consumedAt Instant the token was consumed at.
     */
    public void consume(Instant consumedAt) {
        if (consumedAt == null) {
            throw new IllegalArgumentException(
                    "consumedAt must not be null"
            );
        }

        if (consumedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException(
                    "consumedAt must not be before createdAt"
            );
        }

        if (this.consumedAt != null) {
            throw new IllegalStateException(
                    "Refresh token has already been consumed"
            );
        }

        if (family.getRevokedAt() != null) {
            throw new IllegalStateException(
                    "Refresh token family has been revoked"
            );
        }

        if (!consumedAt.isBefore(expiresAt)) {
            throw new IllegalStateException(
                    "Refresh token has expired"
            );
        }

        if (!consumedAt.isBefore(family.getAbsoluteExpiresAt())) {
            throw new IllegalStateException(
                    "Refresh token family has expired"
            );
        }

        this.consumedAt = consumedAt;
    }

}
