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
 * One planned visit to a request's room. The entry policy and contact consent are snapshotted at
 * creation and immutable afterwards, so a later change to the request never rewrites what the
 * resident consented to for a visit that already happened. Rows are never deleted: cancel and
 * no-access are status changes.
 */
@Entity
@Table(name = "maintenance_visit")
public class MaintenanceVisit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "worker_id", nullable = false, updatable = false)
    private Long workerId;

    @Column(name = "created_by_id", nullable = false, updatable = false)
    private Long createdById;

    @Column(name = "scheduled_start", nullable = false)
    private Instant scheduledStart;

    @Column(name = "scheduled_end", nullable = false)
    private Instant scheduledEnd;

    @Column(name = "actual_started_at")
    private Instant actualStartedAt;

    @Column(name = "actual_ended_at")
    private Instant actualEndedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_policy_snapshot", nullable = false, updatable = false, length = 40)
    private EntryPolicy entryPolicySnapshot;

    @Column(name = "contact_before_entry", nullable = false, updatable = false)
    private boolean contactBeforeEntry;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected MaintenanceVisit() {
    }

    public MaintenanceVisit(Long requestId, Long workerId, Long createdById, Instant scheduledStart,
            Instant scheduledEnd, EntryPolicy entryPolicySnapshot, boolean contactBeforeEntry,
            String note, Instant occurredAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.workerId = Objects.requireNonNull(workerId);
        this.createdById = Objects.requireNonNull(createdById);
        requireWindow(scheduledStart, scheduledEnd);
        this.scheduledStart = scheduledStart;
        this.scheduledEnd = scheduledEnd;
        this.entryPolicySnapshot = Objects.requireNonNull(entryPolicySnapshot);
        this.contactBeforeEntry = contactBeforeEntry;
        this.note = note;
        this.status = VisitStatus.SCHEDULED;
        this.createdAt = Objects.requireNonNull(occurredAt);
        this.updatedAt = occurredAt;
    }

    public boolean isOpen() {
        return status == VisitStatus.SCHEDULED;
    }

    /**
     * Reschedules the window. Returns true when the window actually moved, which is what decides
     * whether the caller appends a reschedule audit row.
     */
    public boolean reschedule(Instant newStart, Instant newEnd, Instant occurredAt) {
        requireOpen();
        requireWindow(newStart, newEnd);
        boolean moved = !newStart.equals(scheduledStart) || !newEnd.equals(scheduledEnd);
        this.scheduledStart = newStart;
        this.scheduledEnd = newEnd;
        this.updatedAt = Objects.requireNonNull(occurredAt);
        return moved;
    }

    public void changeNote(String newNote, Instant occurredAt) {
        requireOpen();
        this.note = newNote;
        this.updatedAt = Objects.requireNonNull(occurredAt);
    }

    public void complete(Instant startedAt, Instant endedAt, String outcomeNote,
            Instant occurredAt) {
        requireOpen();
        Objects.requireNonNull(startedAt);
        Objects.requireNonNull(endedAt);
        if (!startedAt.isBefore(endedAt)) {
            throw new IllegalArgumentException("Actual start must precede actual end.");
        }
        this.actualStartedAt = startedAt;
        this.actualEndedAt = endedAt;
        finish(VisitStatus.COMPLETED, outcomeNote, occurredAt);
    }

    /** Entry failed, so there is no duration to record. */
    public void recordNoAccess(String outcomeNote, Instant occurredAt) {
        requireOpen();
        finish(VisitStatus.NO_ACCESS, requireNote(outcomeNote), occurredAt);
    }

    public void cancel(String outcomeNote, Instant occurredAt) {
        requireOpen();
        finish(VisitStatus.CANCELED, requireNote(outcomeNote), occurredAt);
    }

    private void finish(VisitStatus target, String outcomeNote, Instant occurredAt) {
        this.status = target;
        if (outcomeNote != null) {
            this.note = outcomeNote;
        }
        this.updatedAt = Objects.requireNonNull(occurredAt);
    }

    private String requireNote(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A note is required for this outcome.");
        }
        return value;
    }

    private void requireOpen() {
        if (!isOpen()) {
            throw new IllegalStateException("Visit is " + status + ", not SCHEDULED.");
        }
    }

    private void requireWindow(Instant start, Instant end) {
        Objects.requireNonNull(start);
        Objects.requireNonNull(end);
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Scheduled start must precede scheduled end.");
        }
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

    public Long getCreatedById() {
        return createdById;
    }

    public Instant getScheduledStart() {
        return scheduledStart;
    }

    public Instant getScheduledEnd() {
        return scheduledEnd;
    }

    public Instant getActualStartedAt() {
        return actualStartedAt;
    }

    public Instant getActualEndedAt() {
        return actualEndedAt;
    }

    public VisitStatus getStatus() {
        return status;
    }

    public EntryPolicy getEntryPolicySnapshot() {
        return entryPolicySnapshot;
    }

    public boolean isContactBeforeEntry() {
        return contactBeforeEntry;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
