package com.dormfix.maintenance.application;

/** It carries no content type or size: those are recorded from what the store reports. */
public record RegisterAttachmentCommand(Long requestId, String objectKey, String type,
        String originalFilename) { }
