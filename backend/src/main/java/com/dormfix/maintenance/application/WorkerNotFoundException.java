package com.dormfix.maintenance.application;

/** Raised when an assignment names a user id that does not exist. Answers 404 WORKER_NOT_FOUND. */
public class WorkerNotFoundException extends RuntimeException {
    public WorkerNotFoundException(Long id) {
        super("Worker was not found: " + id);
    }
}
