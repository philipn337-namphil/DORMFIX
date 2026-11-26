package com.dormfix.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.maintenance.application.event.MaintenanceEvent;
import com.dormfix.notification.domain.NotificationType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The ADR-029 recipient table. Ids: reporter 1, worker 2, previous worker 3, admins 7 and 8. */
class NotificationRecipientsTest {
    private static final Long REPORTER = 1L;
    private static final Long WORKER = 2L;
    private static final Long PREVIOUS = 3L;
    private static final Long ADMIN = 7L;

    @Test
    void creationGoesToTheScopedAdministratorsOnly() {
        var event = new MaintenanceEvent.MaintenanceRequestCreated(10L, "DF-10", REPORTER, REPORTER,
                99L);

        assertThat(NotificationRecipients.plan(event, List.of(7L, 8L))).containsExactly(
                Map.entry(7L, NotificationType.REQUEST_SUBMITTED),
                Map.entry(8L, NotificationType.REQUEST_SUBMITTED));
        assertThat(NotificationRecipients.plan(event, List.of())).isEmpty();
    }

    @Test
    void assignmentTellsTheWorkerAndTheReporterButNotTheActingAdministrator() {
        var assigned = new MaintenanceEvent.MaintenanceRequestAssigned(10L, "DF-10", ADMIN,
                REPORTER, WORKER);
        assertThat(NotificationRecipients.plan(assigned, List.of())).containsExactly(
                Map.entry(WORKER, NotificationType.ASSIGNED_TO_YOU),
                Map.entry(REPORTER, NotificationType.REQUEST_ASSIGNED));

        var reassigned = new MaintenanceEvent.MaintenanceRequestReassigned(10L, "DF-10", ADMIN,
                REPORTER, WORKER, PREVIOUS);
        assertThat(NotificationRecipients.plan(reassigned, List.of())).containsExactly(
                Map.entry(WORKER, NotificationType.ASSIGNED_TO_YOU),
                Map.entry(PREVIOUS, NotificationType.UNASSIGNED_FROM_YOU),
                Map.entry(REPORTER, NotificationType.REQUEST_ASSIGNED));
    }

    @Test
    void theActorNeverHearsOfTheirOwnCommand() {
        var closedByReporter = new MaintenanceEvent.MaintenanceClosed(10L, "DF-10", REPORTER,
                REPORTER, WORKER);
        assertThat(NotificationRecipients.plan(closedByReporter, List.of()))
                .containsExactly(Map.entry(WORKER, NotificationType.REQUEST_CLOSED));

        var scheduledByWorker = new MaintenanceEvent.MaintenanceVisitScheduled(10L, "DF-10", WORKER,
                REPORTER, 5L, WORKER);
        assertThat(NotificationRecipients.plan(scheduledByWorker, List.of()))
                .containsExactly(Map.entry(REPORTER, NotificationType.VISIT_SCHEDULED));

        var started = new MaintenanceEvent.MaintenanceStarted(10L, "DF-10", WORKER, REPORTER);
        assertThat(NotificationRecipients.plan(started, List.of()))
                .containsExactly(Map.entry(REPORTER, NotificationType.REQUEST_STARTED));
    }

    @Test
    void oneRecipientGetsOneNotificationEvenWithTwoRoles() {
        // A reporter who is also the visit worker is listed twice by the table.
        var event = new MaintenanceEvent.MaintenanceVisitCanceled(10L, "DF-10", ADMIN, REPORTER,
                5L, REPORTER);
        assertThat(NotificationRecipients.plan(event, List.of())).hasSize(1);
    }

    @Test
    void aStaffOnlyCommentNeverReachesTheReporter() {
        var staffOnly = new MaintenanceEvent.CommentCreated(10L, "DF-10", ADMIN, REPORTER, 4L,
                true, WORKER);
        assertThat(NotificationRecipients.plan(staffOnly, List.of()))
                .containsExactly(Map.entry(WORKER, NotificationType.COMMENT_ADDED));

        var staffOnlyNoAssignee = new MaintenanceEvent.CommentCreated(10L, "DF-10", ADMIN,
                REPORTER, 4L, true, null);
        assertThat(NotificationRecipients.plan(staffOnlyNoAssignee, List.of())).isEmpty();

        var publicByReporter = new MaintenanceEvent.CommentCreated(10L, "DF-10", REPORTER,
                REPORTER, 4L, false, WORKER);
        assertThat(NotificationRecipients.plan(publicByReporter, List.of()))
                .containsExactly(Map.entry(WORKER, NotificationType.COMMENT_ADDED));
    }

    @Test
    void reporterOnlyEventsAndReopen() {
        assertThat(NotificationRecipients.plan(new MaintenanceEvent.MaintenanceRejected(10L,
                "DF-10", ADMIN, REPORTER), List.of()))
                .containsExactly(Map.entry(REPORTER, NotificationType.REQUEST_REJECTED));
        assertThat(NotificationRecipients.plan(new MaintenanceEvent.MaintenanceVisitNoAccess(10L,
                "DF-10", WORKER, REPORTER, 5L, WORKER), List.of()))
                .containsExactly(Map.entry(REPORTER, NotificationType.VISIT_NO_ACCESS));
        assertThat(NotificationRecipients.plan(new MaintenanceEvent.MaintenanceReopened(10L,
                "DF-10", REPORTER, REPORTER, WORKER), List.of()))
                .containsExactly(Map.entry(WORKER, NotificationType.REQUEST_REOPENED));
    }
}
