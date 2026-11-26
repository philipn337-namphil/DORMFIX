package com.dormfix.notification.application;

public class InvalidNotificationQueryException extends RuntimeException {
    public InvalidNotificationQueryException(String message) {
        super(message);
    }
}
