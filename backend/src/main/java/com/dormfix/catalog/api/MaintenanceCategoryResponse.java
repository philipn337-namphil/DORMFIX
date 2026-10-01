package com.dormfix.catalog.api;

import com.dormfix.catalog.application.MaintenanceCategoryQueryResult;
import java.time.Instant;

public record MaintenanceCategoryResponse(Long id, Long parentId, String code, String name,
        String defaultPriority, boolean active, int sortOrder, Instant createdAt, Instant updatedAt) {
    public static MaintenanceCategoryResponse from(MaintenanceCategoryQueryResult category) {
        return new MaintenanceCategoryResponse(category.id(), category.parentId(), category.code(),
                category.name(), category.defaultPriority(), category.active(), category.sortOrder(),
                category.createdAt(), category.updatedAt());
    }
}
