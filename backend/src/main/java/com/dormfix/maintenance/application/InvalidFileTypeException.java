package com.dormfix.maintenance.application;

/** Raised for a content type outside the configured allow-list. Answers 400 INVALID_FILE_TYPE. */
public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException() {
        super("The file type is not allowed.");
    }
}
