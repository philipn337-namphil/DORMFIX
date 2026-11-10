package com.dormfix.maintenance.application;

public record PresignUploadCommand(Long requestId, String type, String originalFilename,
        String contentType, long fileSize) { }
