package com.dormfix.notification.application;

import com.dormfix.maintenance.application.event.MaintenanceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Best effort, at most once (ADR-029). Runs only after the command committed, so a rolled-back
 * command produces nothing. A failure is logged with the event name and request id only, never
 * the notification text, and is swallowed: the command's response has already been decided.
 */
@Component
public class NotificationEventListener {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationRecorder recorder;

    public NotificationEventListener(NotificationRecorder recorder) {
        this.recorder = recorder;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MaintenanceEvent event) {
        try {
            recorder.record(event);
        } catch (RuntimeException error) {
            LOG.warn("Notification recording failed: event={} requestId={} error={}",
                    event.getClass().getSimpleName(), event.requestId(),
                    error.getClass().getSimpleName());
        }
    }
}
