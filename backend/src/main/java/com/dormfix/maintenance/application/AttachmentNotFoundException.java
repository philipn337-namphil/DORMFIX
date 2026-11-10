package com.dormfix.maintenance.application;

/**
 * Raised when an attachment is missing or belongs to a request the caller cannot read. Both answer
 * 404 ATTACHMENT_NOT_FOUND so the download path does not disclose existence.
 */
public class AttachmentNotFoundException extends RuntimeException {
    public AttachmentNotFoundException(Long id) {
        super("Attachment was not found: " + id);
    }
}
