package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.PresignedUploadResult;
import java.time.Instant;
import java.util.Map;

public record PresignedUploadResponse(String objectKey, String uploadUrl, String method,
        Map<String, String> headers, Instant expiresAt) {
    static PresignedUploadResponse from(PresignedUploadResult value) {
        return new PresignedUploadResponse(value.objectKey(), value.uploadUrl(), value.method(),
                value.headers(), value.expiresAt());
    }
}
