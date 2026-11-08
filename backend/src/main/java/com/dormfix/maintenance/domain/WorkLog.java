package com.dormfix.maintenance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Append-only record of work a worker performed. Every column is mapped {@code updatable = false}
 * and there is no mutator, so the row is written once and the append-only rule holds in code as
 * well as in the retention policy. Appending one neither modifies the request nor writes a
 * RequestHistory row: this table is itself the durable record.
 */
@Entity
@Table(name = "work_log")
public class WorkLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "visit_id", updatable = false)
    private Long visitId;

    @Column(name = "worker_id", nullable = false, updatable = false)
    private Long workerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 20)
    private WorkActionType actionType;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WorkLog() {
    }

    public WorkLog(Long requestId, Long visitId, Long workerId, WorkActionType actionType,
            String description, Instant createdAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.visitId = visitId;
        this.workerId = Objects.requireNonNull(workerId);
        this.actionType = Objects.requireNonNull(actionType);
        this.description = requireDescription(description);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    private String requireDescription(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A work log description is required.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getVisitId() {
        return visitId;
    }

    public Long getWorkerId() {
        return workerId;
    }

    public WorkActionType getActionType() {
        return actionType;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
