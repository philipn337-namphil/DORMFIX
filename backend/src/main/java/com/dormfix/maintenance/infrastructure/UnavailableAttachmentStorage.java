package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.AttachmentStorage;
import com.dormfix.maintenance.application.AttachmentStorageUnavailableException;
import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The adapter in effect until a real object store is connected. Every call fails, so the
 * attachment paths answer 503 instead of pretending. It must never be replaced by a stub that
 * reports success: an always-present object would let anyone register metadata for a file that
 * was never uploaded.
 */
@Component
class UnavailableAttachmentStorage implements AttachmentStorage {
    @Override
    public PresignedUpload presignUpload(String objectKey, String contentType, long fileSize,
            Duration lifetime) {
        throw new AttachmentStorageUnavailableException();
    }

    @Override
    public Optional<StoredObject> describe(String objectKey) {
        throw new AttachmentStorageUnavailableException();
    }

    @Override
    public PresignedDownload presignDownload(String objectKey, String originalFilename,
            Duration lifetime) {
        throw new AttachmentStorageUnavailableException();
    }
}
