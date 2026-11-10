package com.dormfix.maintenance.application;

import java.time.Instant;

/** Metadata only. The object key is deliberately absent: it is internal to storage. */
public record AttachmentResult(Long id, Long requestId, Long uploaderId, String type,
        String originalFilename, String contentType, long fileSize, Instant createdAt) { }
