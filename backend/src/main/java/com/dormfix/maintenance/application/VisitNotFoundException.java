package com.dormfix.maintenance.application;

/** Raised when a visit id does not exist. Answers 404 VISIT_NOT_FOUND. */
public class VisitNotFoundException extends RuntimeException {
    public VisitNotFoundException(Long id) {
        super("Visit was not found: " + id);
    }
}
