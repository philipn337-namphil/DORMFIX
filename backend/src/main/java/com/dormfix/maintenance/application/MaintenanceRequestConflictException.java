package com.dormfix.maintenance.application;

public class MaintenanceRequestConflictException extends RuntimeException {
    public MaintenanceRequestConflictException(String message) {
        super(message);
    }
}
