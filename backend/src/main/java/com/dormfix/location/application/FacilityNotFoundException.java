package com.dormfix.location.application;

public class FacilityNotFoundException extends RuntimeException {
    public FacilityNotFoundException(Long id) {
        super("Facility was not found: " + id);
    }
}
