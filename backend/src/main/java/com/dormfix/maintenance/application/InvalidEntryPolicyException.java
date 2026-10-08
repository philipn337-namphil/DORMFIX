package com.dormfix.maintenance.application;

public class InvalidEntryPolicyException extends RuntimeException {
    public InvalidEntryPolicyException() {
        super("Invalid entry policy.");
    }
}
