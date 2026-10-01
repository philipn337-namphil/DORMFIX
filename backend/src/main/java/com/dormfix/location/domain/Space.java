package com.dormfix.location.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "space",
        uniqueConstraints = @UniqueConstraint(name = "uq_space_building_code",
                columnNames = {"building_id", "code"}))
public class Space {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "building_id", nullable = false)
    private Long buildingId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private SpaceType type;

    @Column(name = "floor", nullable = false)
    private int floor;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Space() {
    }

    public Space(Long buildingId, String code, String name, SpaceType type, int floor,
            String description, boolean active, Instant createdAt, Instant updatedAt) {
        this.buildingId = Objects.requireNonNull(buildingId);
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.floor = floor;
        this.description = description;
        this.active = active;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getBuildingId() {
        return buildingId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public SpaceType getType() {
        return type;
    }

    public int getFloor() {
        return floor;
    }

    public String getDescription() {
        return description;
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

    public void updateDetails(String code, String name, SpaceType type, int floor,
            String description, Instant occurredAt) {
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.type = Objects.requireNonNull(type);
        this.floor = floor;
        this.description = description;
        this.updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void deactivate(Instant occurredAt) {
        if (!active) {
            throw new IllegalStateException("Space is already inactive.");
        }
        active = false;
        updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void reactivate(Instant occurredAt) {
        if (active) {
            throw new IllegalStateException("Space is already active.");
        }
        active = true;
        updatedAt = Objects.requireNonNull(occurredAt);
    }
}
