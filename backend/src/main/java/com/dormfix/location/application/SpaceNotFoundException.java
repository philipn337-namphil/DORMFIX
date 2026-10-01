package com.dormfix.location.application;

public class SpaceNotFoundException extends RuntimeException {
    public SpaceNotFoundException(Long id) {
        super("Space was not found: " + id);
    }
}
