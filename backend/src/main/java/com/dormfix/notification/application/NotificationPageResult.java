package com.dormfix.notification.application;

import java.util.List;

public record NotificationPageResult(List<NotificationResult> content, int page, int size,
        long totalElements, int totalPages) { }
