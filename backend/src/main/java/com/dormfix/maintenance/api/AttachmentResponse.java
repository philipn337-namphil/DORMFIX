package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.AttachmentResult;
import java.time.Instant;

/** Metadata only. There is no objectKey field, so no mapping can leak one. */
public record AttachmentResponse(Long id, Long requestId, Long uploaderId, String type,
        String originalFilename, String contentType, long fileSize, Instant createdAt) {
    static AttachmentResponse from(AttachmentResult value) {
        return new AttachmentResponse(value.id(), value.requestId(), value.uploaderId(),
                value.type(), value.originalFilename(), value.contentType(), value.fileSize(),
                value.createdAt());
    }
}
