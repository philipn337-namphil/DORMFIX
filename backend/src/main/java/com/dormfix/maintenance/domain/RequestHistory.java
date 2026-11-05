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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Append-only audit row. There is no mutator and no delete path: a history row is written once as
 * part of the command transaction it describes.
 */
@Entity
@Table(name = "request_history")
public class RequestHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "request_id", nullable = false, updatable = false) private Long requestId;
    @Column(name = "actor_id", updatable = false) private Long actorId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, updatable = false, length = 50) private RequestHistoryEventType eventType;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", updatable = false, length = 30) private RequestStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", updatable = false, length = 30) private RequestStatus newStatus;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "metadata", updatable = false) private String metadata;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected RequestHistory() { }

    public RequestHistory(Long requestId, Long actorId, RequestHistoryEventType eventType,
            RequestStatus previousStatus, RequestStatus newStatus, Instant createdAt) {
        this(requestId, actorId, eventType, previousStatus, newStatus, createdAt, null);
    }

    public RequestHistory(Long requestId, Long actorId, RequestHistoryEventType eventType,
            RequestStatus previousStatus, RequestStatus newStatus, Instant createdAt,
            String metadata) {
        this.requestId = Objects.requireNonNull(requestId);
        this.actorId = actorId;
        this.eventType = Objects.requireNonNull(eventType);
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.metadata = metadata;
    }

    public Long getRequestId() { return requestId; }

    public Long getActorId() { return actorId; }

    public RequestHistoryEventType getEventType() { return eventType; }

    public RequestStatus getPreviousStatus() { return previousStatus; }

    public RequestStatus getNewStatus() { return newStatus; }

    public String getMetadata() { return metadata; }

    public Instant getCreatedAt() { return createdAt; }
}
