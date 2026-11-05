package com.dormfix.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * One assignment of a request to a worker. At most one row per request has a null
 * {@code unassignedAt}; the database enforces that with a partial unique index. Rows are never
 * deleted, so the table is also the assignment history a former assignee reads through.
 */
@Entity
@Table(name = "maintenance_assignment")
public class MaintenanceAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "worker_id", nullable = false, updatable = false)
    private Long workerId;

    @Column(name = "assigned_by_id", nullable = false, updatable = false)
    private Long assignedById;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "unassigned_at")
    private Instant unassignedAt;

    @Column(name = "unassigned_by_id")
    private Long unassignedById;

    @Column(length = 500, updatable = false)
    private String reason;

    protected MaintenanceAssignment() {
    }

    public MaintenanceAssignment(Long requestId, Long workerId, Long assignedById, String reason,
            Instant assignedAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.workerId = Objects.requireNonNull(workerId);
        this.assignedById = Objects.requireNonNull(assignedById);
        this.reason = reason;
        this.assignedAt = Objects.requireNonNull(assignedAt);
    }

    /**
     * Ends this assignment. The row is kept so the assignment history stays complete.
     */
    public void end(Long unassignedById, Instant unassignedAt) {
        if (this.unassignedAt != null) {
            throw new IllegalStateException("Assignment is already ended.");
        }
        Objects.requireNonNull(unassignedById);
        Objects.requireNonNull(unassignedAt);
        if (unassignedAt.isBefore(assignedAt)) {
            throw new IllegalStateException("Assignment cannot end before it started.");
        }
        this.unassignedById = unassignedById;
        this.unassignedAt = unassignedAt;
    }

    public boolean isActive() {
        return unassignedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public Long getAssignedById() {
        return assignedById;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getUnassignedAt() {
        return unassignedAt;
    }

    public Long getUnassignedById() {
        return unassignedById;
    }

    public String getReason() {
        return reason;
    }
}
