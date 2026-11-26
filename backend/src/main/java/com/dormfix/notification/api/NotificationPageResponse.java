package com.dormfix.notification.api;

import com.dormfix.notification.application.NotificationPageResult;
import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> content, int page, int size,
        long totalElements, int totalPages) {
    static NotificationPageResponse from(NotificationPageResult value) {
        return new NotificationPageResponse(
                value.content().stream().map(NotificationResponse::from).toList(), value.page(),
                value.size(), value.totalElements(), value.totalPages());
    }
}
