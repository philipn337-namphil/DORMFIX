package com.dormfix.maintenance.application;

/**
 * Raised for a scheduled or actual window whose start does not precede its end, or for a half
 * supplied pair. Answers 400 INVALID_VISIT_TIME.
 */
public class InvalidVisitTimeException extends RuntimeException {
    public InvalidVisitTimeException(String message) {
        super(message);
    }
}
