package com.dormfix.location.application;

public class BuildingNotFoundException extends RuntimeException {
    public BuildingNotFoundException(Long id) {
        super("Building was not found: " + id);
    }
}
