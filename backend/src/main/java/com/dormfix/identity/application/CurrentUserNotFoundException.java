package com.dormfix.identity.application;

public class CurrentUserNotFoundException extends RuntimeException {
    public CurrentUserNotFoundException() {
        super("The authenticated user does not exist.");
    }
}
