package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * It deliberately has no contentType or fileSize. Those are recorded from what the store reports,
 * so sending them here would only repeat an unverified claim.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record RegisterAttachmentRequest(@NotBlank @Size(max = 500) String objectKey,
        @NotBlank String type, @NotBlank @Size(max = 255) String originalFilename) { }
