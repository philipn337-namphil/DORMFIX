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
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "facility",
        uniqueConstraints = @UniqueConstraint(name = "uq_facility_asset_code",
                columnNames = "asset_code"),
        indexes = @Index(name = "ix_facility_space_id", columnList = "space_id"))
public class Facility {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "space_id", nullable = false)
    private Long spaceId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "facility_type", nullable = false, length = 50)
    private String facilityType;

    @Column(name = "asset_code", length = 100)
    private String assetCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FacilityStatus status;

    @Column(name = "installed_at")
    private LocalDate installedAt;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Facility() {
    }

    public Facility(Long spaceId, String name, String facilityType, String assetCode,
            FacilityStatus status, LocalDate installedAt, String description,
            Instant createdAt, Instant updatedAt) {
        this.spaceId = Objects.requireNonNull(spaceId);
        this.name = Objects.requireNonNull(name);
        this.facilityType = Objects.requireNonNull(facilityType);
        this.assetCode = assetCode;
        this.status = Objects.requireNonNull(status);
        this.installedAt = installedAt;
        this.description = description;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public String getName() {
        return name;
    }

    public String getFacilityType() {
        return facilityType;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public FacilityStatus getStatus() {
        return status;
    }

    public LocalDate getInstalledAt() {
        return installedAt;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateMetadata(String name, String facilityType, String assetCode,
            LocalDate installedAt, String description, Instant occurredAt) {
        this.name = Objects.requireNonNull(name);
        this.facilityType = Objects.requireNonNull(facilityType);
        this.assetCode = assetCode;
        this.installedAt = installedAt;
        this.description = description;
        this.updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void takeOutOfService(Instant occurredAt) {
        if (status != FacilityStatus.ACTIVE) {
            throw new IllegalStateException("Facility is not active.");
        }
        status = FacilityStatus.OUT_OF_SERVICE;
        updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void reactivate(Instant occurredAt) {
        if (status != FacilityStatus.OUT_OF_SERVICE) {
            throw new IllegalStateException("Facility is not out of service.");
        }
        status = FacilityStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void retire(Instant occurredAt) {
        if (status == FacilityStatus.RETIRED) {
            throw new IllegalStateException("Facility is already retired.");
        }
        status = FacilityStatus.RETIRED;
        updatedAt = Objects.requireNonNull(occurredAt);
    }
}
