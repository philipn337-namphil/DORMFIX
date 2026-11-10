package com.dormfix.maintenance.infrastructure;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Attachment limits as configuration. The defaults are the values recorded in ADR-027; the frozen
 * contract states that limits are not yet product constants, so none of them is hard-coded.
 */
@ConfigurationProperties(prefix = "dormfix.attachments")
record AttachmentProperties(
        @DefaultValue("10485760") long maxFileSizeBytes,
        @DefaultValue({"image/jpeg", "image/png", "image/webp", "application/pdf"})
        Set<String> allowedContentTypes,
        @DefaultValue("20") int maxAttachmentsPerRequest,
        @DefaultValue("10m") Duration uploadUrlLifetime,
        @DefaultValue("5m") Duration downloadUrlLifetime) {
}
