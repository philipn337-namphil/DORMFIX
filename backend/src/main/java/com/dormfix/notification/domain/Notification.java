package com.dormfix.notification.domain;

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

/** A recipient's notification. {@code readAt} is the only column written after insert. */
@Entity
@Table(name = "notification")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "recipient_id", nullable = false, updatable = false) private Long recipientId;
    @Column(name = "request_id", updatable = false) private Long requestId;
    @Enumerated(EnumType.STRING) @Column(name = "type", nullable = false, updatable = false, length = 50)
    private NotificationType type;
    @Column(name = "title", nullable = false, updatable = false, length = 150) private String title;
    @Column(name = "message", nullable = false, updatable = false, length = 500) private String message;
    @Column(name = "read_at") private Instant readAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Notification() { }

    public Notification(Long recipientId, Long requestId, NotificationType type,
            String requestNumber, Instant createdAt) {
        this.recipientId = Objects.requireNonNull(recipientId);
        this.requestId = requestId;
        this.type = Objects.requireNonNull(type);
        this.title = type.title();
        this.message = type.message(requestNumber);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    /** Idempotent: a notification that is already read keeps its first read time. */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = Objects.requireNonNull(now);
        }
    }

    public Long getId() { return id; }

    public Long getRecipientId() { return recipientId; }

    public Long getRequestId() { return requestId; }

    public NotificationType getType() { return type; }

    public String getTitle() { return title; }

    public String getMessage() { return message; }

    public Instant getReadAt() { return readAt; }

    public Instant getCreatedAt() { return createdAt; }
}
