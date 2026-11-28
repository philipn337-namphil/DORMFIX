package com.dormfix.identity.application;

public class InvalidUserQueryException extends RuntimeException {
    public InvalidUserQueryException(String message) {
        super(message);
    }
}
