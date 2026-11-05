package com.dormfix.maintenance.application;

/**
 * Raised for a stale submitted version and for an optimistic-locking failure on the request row.
 * Answers 409 VERSION_CONFLICT. It is never used for the active-assignment index violation.
 */
public class RequestVersionConflictException extends RuntimeException {
    public RequestVersionConflictException() {
        super("The request has changed. Reload and try again.");
    }
}
