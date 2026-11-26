package com.dormfix.notification.application;

import java.time.Instant;

public record NotificationResult(Long id, String type, Long requestId, String title,
        String message, boolean read, Instant readAt, Instant createdAt) { }
