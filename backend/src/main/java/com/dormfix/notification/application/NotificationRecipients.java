package com.dormfix.notification.application;

import com.dormfix.maintenance.application.event.MaintenanceEvent;
import com.dormfix.maintenance.application.event.MaintenanceEvent.CommentCreated;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceClosed;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceHeld;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceMarkedDuplicate;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceRejected;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceReopened;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceRequestAssigned;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceRequestCreated;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceRequestReassigned;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceResolved;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceResumed;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceStarted;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceVisitCanceled;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceVisitCompleted;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceVisitNoAccess;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceVisitRescheduled;
import com.dormfix.maintenance.application.event.MaintenanceEvent.MaintenanceVisitScheduled;
import com.dormfix.notification.domain.NotificationType;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ADR-029 recipient table as one pure function. The actor never hears of their own command,
 * each recipient gets at most one notification per event (the first role listed wins), and a
 * STAFF_ONLY comment never reaches the reporter.
 */
final class NotificationRecipients {
    private NotificationRecipients() { }

    /** {@code scopedAdminIds} is consulted only for MaintenanceRequestCreated. */
    static Map<Long, NotificationType> plan(MaintenanceEvent event, List<Long> scopedAdminIds) {
        Plan plan = new Plan(event.actorId());
        Long reporter = event.reporterId();
        switch (event) {
            case MaintenanceRequestCreated e ->
                    scopedAdminIds.forEach(id -> plan.add(id, NotificationType.REQUEST_SUBMITTED));
            case MaintenanceRequestAssigned e -> {
                plan.add(e.workerId(), NotificationType.ASSIGNED_TO_YOU);
                plan.add(reporter, NotificationType.REQUEST_ASSIGNED);
            }
            case MaintenanceRequestReassigned e -> {
                plan.add(e.workerId(), NotificationType.ASSIGNED_TO_YOU);
                plan.add(e.previousWorkerId(), NotificationType.UNASSIGNED_FROM_YOU);
                plan.add(reporter, NotificationType.REQUEST_ASSIGNED);
            }
            case MaintenanceStarted e -> plan.add(reporter, NotificationType.REQUEST_STARTED);
            case MaintenanceHeld e -> plan.add(reporter, NotificationType.REQUEST_HELD);
            case MaintenanceResumed e -> plan.add(reporter, NotificationType.REQUEST_RESUMED);
            case MaintenanceResolved e -> plan.add(reporter, NotificationType.REQUEST_RESOLVED);
            case MaintenanceClosed e -> {
                plan.add(e.endedWorkerId(), NotificationType.REQUEST_CLOSED);
                plan.add(reporter, NotificationType.REQUEST_CLOSED);
            }
            case MaintenanceReopened e -> plan.add(e.workerId(), NotificationType.REQUEST_REOPENED);
            case MaintenanceRejected e -> plan.add(reporter, NotificationType.REQUEST_REJECTED);
            case MaintenanceMarkedDuplicate e ->
                    plan.add(reporter, NotificationType.REQUEST_MARKED_DUPLICATE);
            case MaintenanceVisitScheduled e -> {
                plan.add(reporter, NotificationType.VISIT_SCHEDULED);
                plan.add(e.workerId(), NotificationType.VISIT_SCHEDULED);
            }
            case MaintenanceVisitRescheduled e -> {
                plan.add(reporter, NotificationType.VISIT_RESCHEDULED);
                plan.add(e.workerId(), NotificationType.VISIT_RESCHEDULED);
            }
            case MaintenanceVisitCanceled e -> {
                plan.add(reporter, NotificationType.VISIT_CANCELED);
                plan.add(e.workerId(), NotificationType.VISIT_CANCELED);
            }
            case MaintenanceVisitCompleted e -> plan.add(reporter, NotificationType.VISIT_COMPLETED);
            case MaintenanceVisitNoAccess e -> plan.add(reporter, NotificationType.VISIT_NO_ACCESS);
            case CommentCreated e -> {
                if (!e.staffOnly()) {
                    plan.add(reporter, NotificationType.COMMENT_ADDED);
                }
                plan.add(e.activeAssigneeId(), NotificationType.COMMENT_ADDED);
            }
        }
        return plan.result();
    }

    private static final class Plan {
        private final Long actorId;
        private final Map<Long, NotificationType> recipients = new LinkedHashMap<>();

        Plan(Long actorId) {
            this.actorId = actorId;
        }

        void add(Long recipientId, NotificationType type) {
            if (recipientId != null && !recipientId.equals(actorId)) {
                recipients.putIfAbsent(recipientId, type);
            }
        }

        Map<Long, NotificationType> result() {
            return Collections.unmodifiableMap(recipients);
        }
    }
}
