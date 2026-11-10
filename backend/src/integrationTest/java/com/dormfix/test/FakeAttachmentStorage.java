package com.dormfix.test;

import com.dormfix.maintenance.application.AttachmentStorage;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory object store for integration tests. It lives in the test source set only: the
 * application ships a fail-closed adapter, and a store that always reports success must never be
 * reachable outside tests. A test simulates the client upload with {@link #upload}.
 */
public class FakeAttachmentStorage implements AttachmentStorage {
    private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();

    public void upload(String objectKey, long size, String contentType) {
        objects.put(objectKey, new StoredObject(size, contentType));
    }

    @Override
    public PresignedUpload presignUpload(String objectKey, String contentType, long fileSize,
            Duration lifetime) {
        return new PresignedUpload("https://storage.invalid/upload/" + objectKey + "?sig=fake",
                "PUT", Map.of("Content-Type", contentType, "Content-Length",
                        Long.toString(fileSize)),
                Instant.now().plus(lifetime));
    }

    @Override
    public Optional<StoredObject> describe(String objectKey) {
        return Optional.ofNullable(objects.get(objectKey));
    }

    @Override
    public PresignedDownload presignDownload(String objectKey, String originalFilename,
            Duration lifetime) {
        return new PresignedDownload("https://storage.invalid/download/" + objectKey + "?sig=fake",
                Instant.now().plus(lifetime));
    }
}
