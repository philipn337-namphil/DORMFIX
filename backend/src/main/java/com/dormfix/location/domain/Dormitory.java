package com.dormfix.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "dormitory")
public class Dormitory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Dormitory() {
    }

    public Dormitory(String name, String address, String timezone, boolean active,
            Instant createdAt, Instant updatedAt) {
        this.name = Objects.requireNonNull(name);
        this.address = Objects.requireNonNull(address);
        this.timezone = Objects.requireNonNull(timezone);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateDetails(String name, String address, String timezone, Instant occurredAt) {
        this.name = Objects.requireNonNull(name);
        this.address = Objects.requireNonNull(address);
        this.timezone = Objects.requireNonNull(timezone);
        this.updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void deactivate(Instant occurredAt) {
        if (!active) {
            throw new IllegalStateException("Dormitory is already inactive.");
        }
        active = false;
        updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void reactivate(Instant occurredAt) {
        if (active) {
            throw new IllegalStateException("Dormitory is already active.");
        }
        active = true;
        updatedAt = Objects.requireNonNull(occurredAt);
    }
}
