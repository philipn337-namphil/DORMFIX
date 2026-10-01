package com.dormfix.catalog.application;

public record CreateMaintenanceCategoryCommand(Long parentId, String code, String name,
        String defaultPriority, int sortOrder) {
}
