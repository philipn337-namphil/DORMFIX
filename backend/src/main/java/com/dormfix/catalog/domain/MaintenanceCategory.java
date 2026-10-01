package com.dormfix.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "maintenance_category",
        uniqueConstraints = @UniqueConstraint(name = "uq_maintenance_category_code",
                columnNames = "code"),
        indexes = @Index(name = "ix_maintenance_category_parent_id", columnList = "parent_id"))
public class MaintenanceCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_priority", nullable = false, length = 20)
    private Priority defaultPriority;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaintenanceCategory() {
    }

    public MaintenanceCategory(Long parentId, String code, String name, Priority defaultPriority,
            boolean active, int sortOrder, Instant createdAt, Instant updatedAt) {
        this.parentId = parentId;
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.defaultPriority = Objects.requireNonNull(defaultPriority);
        this.active = active;
        this.sortOrder = sortOrder;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Priority getDefaultPriority() {
        return defaultPriority;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateMetadata(String code, String name, Priority defaultPriority, int sortOrder,
            Instant occurredAt) {
        this.code = Objects.requireNonNull(code);
        this.name = Objects.requireNonNull(name);
        this.defaultPriority = Objects.requireNonNull(defaultPriority);
        this.sortOrder = sortOrder;
        this.updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void deactivate(Instant occurredAt) {
        if (!active) {
            throw new IllegalStateException("Maintenance category is already inactive.");
        }
        active = false;
        updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void reactivate(Instant occurredAt) {
        if (active) {
            throw new IllegalStateException("Maintenance category is already active.");
        }
        active = true;
        updatedAt = Objects.requireNonNull(occurredAt);
    }
}
