package com.dormfix.notification.api;

import com.dormfix.notification.application.NotificationResult;
import java.time.Instant;

public record NotificationResponse(Long id, String type, Long requestId, String title,
        String message, boolean read, Instant readAt, Instant createdAt) {
    static NotificationResponse from(NotificationResult value) {
        return new NotificationResponse(value.id(), value.type(), value.requestId(), value.title(),
                value.message(), value.read(), value.readAt(), value.createdAt());
    }
}
