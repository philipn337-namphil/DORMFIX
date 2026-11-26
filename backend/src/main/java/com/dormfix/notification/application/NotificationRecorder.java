package com.dormfix.notification.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.maintenance.application.event.MaintenanceEvent;
import com.dormfix.notification.domain.Notification;
import com.dormfix.notification.domain.NotificationType;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the notifications for one committed event in its own transaction. The business command
 * has already committed, so this transaction must be new; its failure cannot touch that command.
 */
@Service
public class NotificationRecorder {
    private final NotificationRepository notifications;
    private final AdminDormitoryScopeRepository adminScopes;
    private final Clock clock;

    public NotificationRecorder(NotificationRepository notifications,
            AdminDormitoryScopeRepository adminScopes, Clock clock) {
        this.notifications = notifications;
        this.adminScopes = adminScopes;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int record(MaintenanceEvent event) {
        List<Long> admins = event instanceof MaintenanceEvent.MaintenanceRequestCreated created
                ? adminScopes.findUserIdsByDormitoryId(created.dormitoryId())
                : List.of();
        Map<Long, NotificationType> plan = NotificationRecipients.plan(event, admins);
        if (plan.isEmpty()) {
            return 0;
        }
        Instant now = Instant.now(clock);
        List<Notification> rows = new ArrayList<>(plan.size());
        plan.forEach((recipient, type) -> rows.add(new Notification(recipient, event.requestId(),
                type, event.requestNumber(), now)));
        notifications.saveAll(rows);
        return rows.size();
    }
}
