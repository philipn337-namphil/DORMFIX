package com.dormfix.maintenance.application;

/**
 * Raised when a request does not exist, and also when it exists outside the caller read
 * visibility. Both answer 404 REQUEST_NOT_FOUND so a read path never discloses the existence of a
 * request the caller may not read. Commands keep 403 instead.
 */
public class MaintenanceRequestNotFoundException extends RuntimeException {
    public MaintenanceRequestNotFoundException(Long id) {
        super("Maintenance request was not found: " + id);
    }
}
