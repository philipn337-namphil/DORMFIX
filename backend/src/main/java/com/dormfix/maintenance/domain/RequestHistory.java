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

@Entity
@Table(name = "request_history")
public class RequestHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "request_id", nullable = false) private Long requestId;
    @Column(name = "actor_id") private Long actorId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 50) private RequestHistoryEventType eventType;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status", length = 30) private RequestStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", length = 30) private RequestStatus newStatus;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected RequestHistory() { }
    public RequestHistory(Long requestId, Long actorId, RequestHistoryEventType eventType, RequestStatus previousStatus, RequestStatus newStatus, Instant createdAt) {
        this.requestId=Objects.requireNonNull(requestId); this.actorId=actorId; this.eventType=Objects.requireNonNull(eventType); this.previousStatus=previousStatus; this.newStatus=newStatus; this.createdAt=Objects.requireNonNull(createdAt);
    }
    public Long getRequestId(){return requestId;} public Long getActorId(){return actorId;} public RequestHistoryEventType getEventType(){return eventType;} public RequestStatus getNewStatus(){return newStatus;}
}
