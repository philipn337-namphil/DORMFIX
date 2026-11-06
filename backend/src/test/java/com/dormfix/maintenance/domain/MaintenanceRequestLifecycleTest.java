package com.dormfix.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.catalog.domain.Priority;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MaintenanceRequestLifecycleTest {
    private static final Instant T1 = Instant.parse("2026-11-04T01:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-04T02:00:00Z");
    private static final Instant T3 = Instant.parse("2026-11-04T03:00:00Z");

    @Test
    void theHappyPathWalksReportedToClosed() {
        MaintenanceRequest request = reported();

        assertThat(request.markAssigned(T1)).isEqualTo(RequestStatus.REPORTED);
        assertThat(request.start(T1, null)).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(request.resolve(T2, "fixed")).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.RESOLVED);
        assertThat(request.getResolvedAt()).isEqualTo(T2);
        assertThat(request.getClosedAt()).isNull();

        assertThat(request.close(T3, null)).isEqualTo(RequestStatus.RESOLVED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.CLOSED);
        assertThat(request.getClosedAt()).isEqualTo(T3);
        assertThat(request.getResolvedAt()).isEqualTo(T2);
    }

    @Test
    void holdAndResumeMoveBetweenInProgressAndOnHold() {
        MaintenanceRequest request = inProgress();

        assertThat(request.hold(T2, "waiting for a part")).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.ON_HOLD);
        assertThat(request.getStatusReason()).isEqualTo("waiting for a part");

        assertThat(request.resume(T3, null)).isEqualTo(RequestStatus.ON_HOLD);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
    }

    @Test
    void statusReasonIsAProjectionSoATransitionWithoutOneClearsIt() {
        MaintenanceRequest request = inProgress();
        request.hold(T2, "waiting for a part");

        request.resume(T3, null);

        assertThat(request.getStatusReason()).isNull();
    }

    @Test
    void reopenClearsResolvedAtAndLeavesClosedAtAlone() {
        MaintenanceRequest request = inProgress();
        request.resolve(T2, "fixed");

        assertThat(request.reopen(T3, "still leaking")).isEqualTo(RequestStatus.RESOLVED);

        assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
        assertThat(request.getResolvedAt()).isNull();
        assertThat(request.getClosedAt()).isNull();
        assertThat(request.getStatusReason()).isEqualTo("still leaking");
    }

    @Test
    void resolvingAgainAfterAReopenOverwritesResolvedAt() {
        MaintenanceRequest request = inProgress();
        request.resolve(T1, "fixed");
        request.reopen(T2, "still leaking");

        request.resolve(T3, "fixed again");

        assertThat(request.getResolvedAt()).isEqualTo(T3);
    }

    @Test
    void closedIsTerminalForEveryCommandIncludingAssignment() {
        MaintenanceRequest request = inProgress();
        request.resolve(T1, null);
        request.close(T2, null);

        assertThat(request.isAssignable()).isFalse();
        assertThatThrownBy(() -> request.reopen(T3, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.start(T3, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.markAssigned(T3)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> request.reject(T3, "no")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectAndMarkDuplicateOnlyLeaveReportedAndAreTerminal() {
        MaintenanceRequest rejected = reported();
        assertThat(rejected.reject(T1, "not a fault")).isEqualTo(RequestStatus.REPORTED);
        assertThat(rejected.getStatus()).isEqualTo(RequestStatus.REJECTED);
        assertThat(rejected.getResolvedAt()).isNull();
        assertThat(rejected.getClosedAt()).isNull();
        assertThat(rejected.isAssignable()).isFalse();

        MaintenanceRequest duplicate = reported();
        assertThat(duplicate.markDuplicate(77L, T1, "same as 77")).isEqualTo(RequestStatus.REPORTED);
        assertThat(duplicate.getStatus()).isEqualTo(RequestStatus.DUPLICATE);
        assertThat(duplicate.getDuplicateOfId()).isEqualTo(77L);
        assertThat(duplicate.isAssignable()).isFalse();
    }

    @Test
    void everyCommandRejectsAWrongSourceState() {
        assertThatThrownBy(() -> reported().start(T1, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reported().hold(T1, "x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reported().resume(T1, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reported().resolve(T1, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reported().close(T1, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reported().reopen(T1, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inProgress().reject(T1, "x"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inProgress().markDuplicate(77L, T1, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void commandsAreNotIdempotent() {
        MaintenanceRequest request = inProgress();

        assertThatThrownBy(() -> request.start(T2, null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void isInReportsTheCurrentStatusOnly() {
        MaintenanceRequest request = reported();

        assertThat(request.isIn(RequestStatus.REPORTED)).isTrue();
        assertThat(request.isIn(RequestStatus.CLOSED, RequestStatus.REPORTED)).isTrue();
        assertThat(request.isIn(RequestStatus.CLOSED, RequestStatus.DUPLICATE)).isFalse();
    }

    private MaintenanceRequest reported() {
        return new MaintenanceRequest(1L, 2L, null, 3L, "Leak", "Ceiling leak", Priority.HIGH,
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, null, null,
                Instant.parse("2026-11-04T00:00:00Z"));
    }

    private MaintenanceRequest inProgress() {
        MaintenanceRequest request = reported();
        request.markAssigned(T1);
        request.start(T1, null);
        return request;
    }
}
