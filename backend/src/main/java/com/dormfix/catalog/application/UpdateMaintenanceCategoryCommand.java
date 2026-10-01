package com.dormfix.catalog.application;

public record UpdateMaintenanceCategoryCommand(String code, String name, String defaultPriority,
        int sortOrder) {
}
