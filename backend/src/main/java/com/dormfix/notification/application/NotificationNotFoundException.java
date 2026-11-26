package com.dormfix.notification.application;

/** Missing and another user's notification are indistinguishable on purpose. */
public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(Long id) {
        super("Notification was not found.");
    }
}
