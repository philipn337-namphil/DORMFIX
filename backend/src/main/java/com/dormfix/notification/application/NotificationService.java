package com.dormfix.notification.application;

import com.dormfix.notification.domain.Notification;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The caller's own notifications. Every operation is keyed by the authenticated user id. */
@Service
public class NotificationService {
    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 100;

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPageResult list(Long recipientId, boolean unreadOnly, int page, int size) {
        if (page < 0 || size < MIN_SIZE || size > MAX_SIZE) {
            throw new InvalidNotificationQueryException("Invalid pagination.");
        }
        List<Notification> rows = unreadOnly
                ? notifications.findUnreadPage(recipientId, page, size)
                : notifications.findPage(recipientId, page, size);
        long total = unreadOnly ? notifications.countUnread(recipientId)
                : notifications.count(recipientId);
        int totalPages = (int) Math.ceil((double) total / size);
        return new NotificationPageResult(rows.stream().map(NotificationService::result).toList(),
                page, size, total, totalPages);
    }

    @Transactional
    public NotificationResult markRead(Long id, Long recipientId) {
        Notification notification = notifications.findOwn(id, recipientId)
                .orElseThrow(() -> new NotificationNotFoundException(id));
        notification.markRead(now());
        return result(notifications.save(notification));
    }

    @Transactional
    public void markAllRead(Long recipientId) {
        notifications.markAllRead(recipientId, now());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long recipientId) {
        return notifications.countUnread(recipientId);
    }

    /**
     * TIMESTAMPTZ keeps microseconds. Truncating first makes the readAt returned now equal to the
     * one every later read returns, which is what the idempotent read contract promises.
     */
    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private static NotificationResult result(Notification value) {
        return new NotificationResult(value.getId(), value.getType().name(), value.getRequestId(),
                value.getTitle(), value.getMessage(), value.getReadAt() != null,
                value.getReadAt(), value.getCreatedAt());
    }
}
