package com.dormfix.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

@Entity
@Table(
        name = "refresh_token_sessions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_refresh_token_sessions_token_hash",
                columnNames = "token_hash"),
        indexes = @Index(name = "ix_refresh_token_sessions_user_id", columnList = "user_id"))
public class RefreshTokenSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "bytea")
    private byte[] tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshTokenSession() {
    }

    public RefreshTokenSession(Long userId, byte[] tokenHash, Instant expiresAt, Instant createdAt) {
        this.userId = Objects.requireNonNull(userId);
        this.tokenHash = Arrays.copyOf(Objects.requireNonNull(tokenHash), tokenHash.length);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public byte[] getTokenHash() {
        return Arrays.copyOf(tokenHash, tokenHash.length);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void revoke(Instant occurredAt) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(occurredAt);
        }
    }
}
