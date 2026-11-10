package com.dormfix.maintenance.application;

/** Raised for a size above the configured limit or not positive. Answers 400 FILE_TOO_LARGE. */
public class FileTooLargeException extends RuntimeException {
    public FileTooLargeException() {
        super("The file size is not allowed.");
    }
}
