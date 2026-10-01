package com.dormfix.location.application;

public class StructureStateConflictException extends RuntimeException {
    private final String code;

    public StructureStateConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
