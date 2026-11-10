package com.dormfix.maintenance.application;

import java.time.Instant;
import java.util.Map;

public record PresignedUploadResult(String objectKey, String uploadUrl, String method,
        Map<String, String> headers, Instant expiresAt) { }
