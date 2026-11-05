package com.dormfix.maintenance.application;

/** Raised for out-of-range pagination and unparsable filter values. Answers 400. */
public class InvalidMaintenanceQueryException extends RuntimeException {
    public InvalidMaintenanceQueryException(String message) {
        super(message);
    }
}
