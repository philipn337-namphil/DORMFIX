package com.dormfix.maintenance.application;

/**
 * Raised when the named user exists but cannot be assigned: it does not hold the WORKER role, or
 * its status is not ACTIVE. Answers 409 INVALID_WORKER, because the payload is well formed and the
 * failure is the referenced entity state.
 */
public class InvalidWorkerException extends RuntimeException {
    public InvalidWorkerException() {
        super("Worker cannot be assigned.");
    }
}
