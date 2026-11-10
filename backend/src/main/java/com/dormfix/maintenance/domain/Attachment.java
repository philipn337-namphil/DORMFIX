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
 * Metadata of one uploaded file. The binary lives in object storage; this row holds the key and
 * what the store reported about the object. Every column is {@code updatable = false} and there is
 * no mutator: V1 neither edits nor deletes an attachment.
 */
@Entity
@Table(name = "attachment")
public class Attachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private Long requestId;

    @Column(name = "uploader_id", nullable = false, updatable = false)
    private Long uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private AttachmentType type;

    @Column(name = "object_key", nullable = false, updatable = false, unique = true, length = 500)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, updatable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, updatable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false, updatable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Attachment() {
    }

    public Attachment(Long requestId, Long uploaderId, AttachmentType type, String objectKey,
            String originalFilename, String contentType, long fileSize, Instant createdAt) {
        this.requestId = Objects.requireNonNull(requestId);
        this.uploaderId = Objects.requireNonNull(uploaderId);
        this.type = Objects.requireNonNull(type);
        this.objectKey = requireText(objectKey, "objectKey");
        this.originalFilename = requireText(originalFilename, "originalFilename");
        this.contentType = requireText(contentType, "contentType");
        if (fileSize <= 0) {
            throw new IllegalArgumentException("fileSize must be positive.");
        }
        this.fileSize = fileSize;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
        return value;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public Long getUploaderId() {
        return uploaderId;
    }

    public AttachmentType getType() {
        return type;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
