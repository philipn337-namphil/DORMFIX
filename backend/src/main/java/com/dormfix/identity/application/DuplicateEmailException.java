package com.dormfix.identity.application;

public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() {
        super("The normalized email already exists.");
    }
}
