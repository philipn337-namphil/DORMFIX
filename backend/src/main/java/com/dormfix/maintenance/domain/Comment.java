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
 * A comment on a request. It cannot be edited: every column except {@code deletedAt} is mapped
 * {@code updatable = false}, and the only mutator is the soft delete. The row is never removed.
 */
@Entity
@Table(name = "comment")
public class Comment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private CommentVisibility visibility;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Comment() {
    }

    public Comment(Long requestId, Long authorId, String content, CommentVisibility visibility,
            Instant createdAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.authorId = Objects.requireNonNull(authorId);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Comment content is required.");
        }
        this.content = content;
        this.visibility = Objects.requireNonNull(visibility);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public void softDelete(Instant occurredAt) {
        if (deletedAt != null) {
            throw new IllegalStateException("Comment is already deleted.");
        }
        Objects.requireNonNull(occurredAt);
        if (occurredAt.isBefore(createdAt)) {
            throw new IllegalStateException("Comment cannot be deleted before it was created.");
        }
        this.deletedAt = occurredAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPublic() {
        return visibility == CommentVisibility.PUBLIC;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public String getContent() {
        return content;
    }

    public CommentVisibility getVisibility() {
        return visibility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
