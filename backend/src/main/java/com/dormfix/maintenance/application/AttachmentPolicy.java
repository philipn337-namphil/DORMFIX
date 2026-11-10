package com.dormfix.maintenance.application;

import java.time.Duration;
import java.util.Set;

/**
 * Attachment limits. They are configuration rather than constants because the frozen contract
 * states that limits are not yet product constants.
 */
public record AttachmentPolicy(long maxFileSizeBytes, Set<String> allowedContentTypes,
        int maxAttachmentsPerRequest, Duration uploadUrlLifetime, Duration downloadUrlLifetime) {
    public AttachmentPolicy {
        allowedContentTypes = Set.copyOf(allowedContentTypes);
        if (maxFileSizeBytes <= 0 || maxAttachmentsPerRequest <= 0) {
            throw new IllegalArgumentException("Attachment limits must be positive.");
        }
    }
}
