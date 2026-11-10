package com.dormfix.maintenance.application;

/**
 * Raised when object storage is not connected. Answers 503 STORAGE_UNAVAILABLE. Failing closed is
 * deliberate: a store that pretended to work would let anyone register metadata for a file that
 * does not exist.
 */
public class AttachmentStorageUnavailableException extends RuntimeException {
    public AttachmentStorageUnavailableException() {
        super("Attachment storage is not available.");
    }
}
