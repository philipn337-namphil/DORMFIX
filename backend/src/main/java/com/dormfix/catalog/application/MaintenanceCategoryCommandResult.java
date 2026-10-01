package com.dormfix.catalog.application;

import java.time.Instant;

public record MaintenanceCategoryCommandResult(Long id, Long parentId, String code, String name,
        String defaultPriority, boolean active, int sortOrder, Instant createdAt, Instant updatedAt) {
}
