package com.dormfix.notification.infrastructure;

import com.dormfix.notification.domain.Notification;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataNotificationRepository extends JpaRepository<Notification, Long> {
    Optional<Notification> findByIdAndRecipientId(Long id, Long recipientId);

    List<Notification> findAllByRecipientIdOrderByCreatedAtDescIdDesc(Long recipientId,
            Pageable pageable);

    long countByRecipientId(Long recipientId);

    List<Notification> findAllByRecipientIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
            Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :now"
            + " where n.recipientId = :recipientId and n.readAt is null")
    int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);
}
