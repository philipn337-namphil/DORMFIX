package com.dormfix.notification.infrastructure;

import com.dormfix.notification.application.NotificationRepository;
import com.dormfix.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaNotificationRepositoryAdapter implements NotificationRepository {
    private final SpringDataNotificationRepository delegate;

    JpaNotificationRepositoryAdapter(SpringDataNotificationRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<Notification> saveAll(List<Notification> notifications) {
        return delegate.saveAll(notifications);
    }

    @Override
    public Notification save(Notification notification) {
        return delegate.save(notification);
    }

    @Override
    public Optional<Notification> findOwn(Long id, Long recipientId) {
        return delegate.findByIdAndRecipientId(id, recipientId);
    }

    @Override
    public List<Notification> findPage(Long recipientId, int page, int size) {
        return delegate.findAllByRecipientIdOrderByCreatedAtDescIdDesc(recipientId,
                PageRequest.of(page, size));
    }

    @Override
    public long count(Long recipientId) {
        return delegate.countByRecipientId(recipientId);
    }

    @Override
    public List<Notification> findUnreadPage(Long recipientId, int page, int size) {
        return delegate.findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(recipientId,
                PageRequest.of(page, size));
    }

    @Override
    public long countUnread(Long recipientId) {
        return delegate.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    @Override
    public int markAllRead(Long recipientId, Instant now) {
        return delegate.markAllRead(recipientId, now);
    }
}
