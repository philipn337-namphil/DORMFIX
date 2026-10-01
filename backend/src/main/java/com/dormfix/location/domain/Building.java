package com.dormfix.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "building",
        uniqueConstraints = @UniqueConstraint(name = "uq_building_dormitory_code",
                columnNames = {"dormitory_id", "code"}))
public class Building {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "dormitory_id", nullable = false)
    private Long dormitoryId;

    @Column(name = "code", nullable = false, length = 30)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Building() {
    }

    public Building(Long dormitoryId, String code, String name, boolean active,
            Instant createdAt, Instant updatedAt) {
        this.dormitoryId = Objects.requireNonNull(dormitoryId);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getDormitoryId() {
        return dormitoryId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
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

    public void updateDetails(String code, String name, Instant occurredAt) {
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void deactivate(Instant occurredAt) {
        if (!active) {
            throw new IllegalStateException("Building is already inactive.");
        }
        active = false;
        updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void reactivate(Instant occurredAt) {
        if (active) {
            throw new IllegalStateException("Building is already active.");
        }
        active = true;
        updatedAt = Objects.requireNonNull(occurredAt);
    }
}
