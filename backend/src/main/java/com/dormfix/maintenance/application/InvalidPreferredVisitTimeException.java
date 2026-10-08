package com.dormfix.maintenance.application;

public class InvalidPreferredVisitTimeException extends RuntimeException {
    public InvalidPreferredVisitTimeException() {
        super("Invalid preferred visit time.");
    }
}
