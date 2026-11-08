package com.dormfix.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MaintenanceVisitTest {
    private static final Instant T0 = Instant.parse("2026-11-07T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-11-07T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-07T11:00:00Z");

    @Test
    void aNewVisitIsOpenAndCarriesTheConsentSnapshot() {
        MaintenanceVisit visit = scheduled();

        assertThat(visit.isOpen()).isTrue();
        assertThat(visit.getStatus()).isEqualTo(VisitStatus.SCHEDULED);
        assertThat(visit.getEntryPolicySnapshot())
                .isEqualTo(EntryPolicy.RESIDENT_PRESENT_REQUIRED);
        assertThat(visit.isContactBeforeEntry()).isTrue();
        assertThat(visit.getActualStartedAt()).isNull();
        assertThat(visit.getActualEndedAt()).isNull();
    }

    @Test
    void aScheduledWindowMustHaveStartBeforeEnd() {
        assertThatThrownBy(() -> new MaintenanceVisit(1L, 2L, 3L, T2, T1,
                EntryPolicy.ABSENT_ENTRY_ALLOWED, false, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MaintenanceVisit(1L, 2L, 3L, T1, T1,
                EntryPolicy.ABSENT_ENTRY_ALLOWED, false, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aPastWindowIsAcceptedBecauseAVisitMayBeRecordedAfterTheFact() {
        MaintenanceVisit visit = new MaintenanceVisit(1L, 2L, 3L,
                Instant.parse("2020-01-01T09:00:00Z"), Instant.parse("2020-01-01T10:00:00Z"),
                EntryPolicy.ABSENT_ENTRY_ALLOWED, false, null, T0);

        assertThat(visit.isOpen()).isTrue();
    }

    @Test
    void reschedulingReportsWhetherTheWindowActuallyMoved() {
        MaintenanceVisit visit = scheduled();

        assertThat(visit.reschedule(T1, T2, T2)).isFalse();
        assertThat(visit.reschedule(T0, T2, T2)).isTrue();
        assertThat(visit.getScheduledStart()).isEqualTo(T0);
        assertThat(visit.getUpdatedAt()).isEqualTo(T2);
    }

    @Test
    void completeStoresBothActualTimestampsAndIsTerminal() {
        MaintenanceVisit visit = scheduled();

        visit.complete(T1, T2, "replaced the trap", T2);

        assertThat(visit.getStatus()).isEqualTo(VisitStatus.COMPLETED);
        assertThat(visit.getActualStartedAt()).isEqualTo(T1);
        assertThat(visit.getActualEndedAt()).isEqualTo(T2);
        assertThat(visit.getNote()).isEqualTo("replaced the trap");
        assertThat(visit.isOpen()).isFalse();
    }

    @Test
    void completeRejectsAnInvertedActualWindow() {
        assertThatThrownBy(() -> scheduled().complete(T2, T1, null, T2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduled().complete(T1, T1, null, T2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noAccessAndCancelRequireANoteAndRecordNoDuration() {
        MaintenanceVisit noAccess = scheduled();
        noAccess.recordNoAccess("nobody home", T2);
        assertThat(noAccess.getStatus()).isEqualTo(VisitStatus.NO_ACCESS);
        assertThat(noAccess.getActualStartedAt()).isNull();
        assertThat(noAccess.getActualEndedAt()).isNull();

        MaintenanceVisit canceled = scheduled();
        canceled.cancel("resident rescheduled", T2);
        assertThat(canceled.getStatus()).isEqualTo(VisitStatus.CANCELED);
        assertThat(canceled.getActualEndedAt()).isNull();

        assertThatThrownBy(() -> scheduled().recordNoAccess("  ", T2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> scheduled().cancel(null, T2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void everyOutcomeIsTerminalSoNothingElseMayFollow() {
        MaintenanceVisit visit = scheduled();
        visit.complete(T1, T2, null, T2);

        assertThatThrownBy(() -> visit.cancel("x", T2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> visit.recordNoAccess("x", T2))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> visit.reschedule(T0, T2, T2))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> visit.changeNote("x", T2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theConsentSnapshotHasNoMutator() {
        assertThat(java.util.Arrays.stream(MaintenanceVisit.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(n -> n.startsWith("setEntryPolicy") || n.startsWith("setContact")
                        || n.equals("setStatus") || n.startsWith("setWorker"))).isEmpty();
    }

    private MaintenanceVisit scheduled() {
        return new MaintenanceVisit(1L, 2L, 3L, T1, T2, EntryPolicy.RESIDENT_PRESENT_REQUIRED,
                true, "bring a ladder", T0);
    }
}
