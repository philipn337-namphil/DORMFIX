package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.DownloadUrlResult;
import java.time.Instant;

public record DownloadUrlResponse(String url, Instant expiresAt) {
    static DownloadUrlResponse from(DownloadUrlResult value) {
        return new DownloadUrlResponse(value.url(), value.expiresAt());
    }
}
