package com.dormfix.maintenance.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Boundary to object storage. The application depends on this port only; the real S3 adapter is a
 * later slice. Implementations must never be called inside a database transaction, and callers
 * must never log the URLs it returns.
 */
public interface AttachmentStorage {
    /** Issues an upload URL bound to the declared content type and size. */
    PresignedUpload presignUpload(String objectKey, String contentType, long fileSize,
            Duration lifetime);

    /** Reports what the store actually holds for the key, or empty when nothing was uploaded. */
    Optional<StoredObject> describe(String objectKey);

    PresignedDownload presignDownload(String objectKey, String originalFilename,
            Duration lifetime);

    record PresignedUpload(String url, String method, Map<String, String> headers,
            Instant expiresAt) { }

    record StoredObject(long size, String contentType) { }

    record PresignedDownload(String url, Instant expiresAt) { }
}
