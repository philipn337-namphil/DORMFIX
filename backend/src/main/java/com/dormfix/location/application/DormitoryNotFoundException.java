package com.dormfix.location.application;

public class DormitoryNotFoundException extends RuntimeException {
    public DormitoryNotFoundException(Long id) {
        super("Dormitory was not found: " + id);
    }
}
