package com.dormfix.catalog.application;

public class MaintenanceCategoryNotFoundException extends RuntimeException {
    public MaintenanceCategoryNotFoundException(Long id) {
        super("Maintenance category was not found: " + id);
    }
}
