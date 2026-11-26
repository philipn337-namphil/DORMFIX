package com.dormfix.notification.application;

import com.dormfix.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Every read is keyed by the recipient, so no method can return another user's notification. */
public interface NotificationRepository {
    List<Notification> saveAll(List<Notification> notifications);

    Notification save(Notification notification);

    Optional<Notification> findOwn(Long id, Long recipientId);

    /** Ordered createdAt DESC, id DESC. */
    List<Notification> findPage(Long recipientId, int page, int size);

    long count(Long recipientId);

    /** Unread only, ordered createdAt DESC, id DESC. */
    List<Notification> findUnreadPage(Long recipientId, int page, int size);

    long countUnread(Long recipientId);

    /** Marks every unread notification of the recipient read and returns how many changed. */
    int markAllRead(Long recipientId, Instant now);
}
