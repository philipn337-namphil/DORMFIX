package com.dormfix.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.catalog.domain.Priority;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MaintenanceRequestPatchTest {
    private static final Instant CREATED = Instant.parse("2026-11-10T00:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-10T01:00:00Z");
    private static final Instant START = Instant.parse("2026-11-11T10:00:00Z");
    private static final Instant END = Instant.parse("2026-11-11T11:00:00Z");

    @Test
    void reviseReportReturnsOnlyTheNamesThatActuallyChanged() {
        MaintenanceRequest request = reported();

        var changed = request.reviseReport("Bigger leak", "Ceiling leak",
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, false, null, null, LATER);

        assertThat(changed).containsExactly("title", "contactBeforeEntry");
        assertThat(request.getTitle()).isEqualTo("Bigger leak");
        assertThat(request.isContactBeforeEntry()).isFalse();
        assertThat(request.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void reviseReportWithIdenticalValuesTouchesNothing() {
        MaintenanceRequest request = reported();

        var changed = request.reviseReport("Leak", "Ceiling leak",
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, null, null, LATER);

        assertThat(changed).isEmpty();
        assertThat(request.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void reviseReportSetsAndClearsThePreferredWindowAsAPair() {
        MaintenanceRequest request = reported();

        assertThat(request.reviseReport("Leak", "Ceiling leak",
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, START, END, LATER))
                .containsExactly("preferredVisitStart", "preferredVisitEnd");
        assertThat(request.reviseReport("Leak", "Ceiling leak",
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, null, null, LATER))
                .containsExactly("preferredVisitStart", "preferredVisitEnd");
        assertThat(request.getPreferredVisitStart()).isNull();
    }

    @Test
    void reviseReportRejectsAHalfOrInvertedPreferredWindow() {
        assertThatThrownBy(() -> reported().reviseReport("Leak", "Ceiling leak",
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, START, null, LATER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reported().reviseReport("Leak", "Ceiling leak",
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, END, START, LATER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reviseReportIsOnlyPossibleWhileReported() {
        MaintenanceRequest request = reported();
        request.markAssigned(LATER);

        assertThatThrownBy(() -> request.reviseReport("x", "y",
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, null, null, LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reclassifyChangesCategoryWithoutResettingPriority() {
        MaintenanceRequest request = reported();

        var changed = request.reclassify(99L, Priority.HIGH, null, LATER);

        assertThat(changed).containsExactly("categoryId");
        assertThat(request.getCategoryId()).isEqualTo(99L);
        assertThat(request.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    void reclassifySetsReplacesAndClearsTheFacility() {
        MaintenanceRequest request = reported();

        assertThat(request.reclassify(3L, Priority.HIGH, 7L, LATER)).containsExactly("facilityId");
        assertThat(request.reclassify(3L, Priority.HIGH, 8L, LATER)).containsExactly("facilityId");
        assertThat(request.reclassify(3L, Priority.HIGH, null, LATER))
                .containsExactly("facilityId");
        assertThat(request.getFacilityId()).isNull();
    }

    @Test
    void reclassifyReportsCategoryAndPriorityTogetherInAStableOrder() {
        MaintenanceRequest request = reported();

        assertThat(request.reclassify(99L, Priority.EMERGENCY, 7L, LATER))
                .containsExactly("categoryId", "priority", "facilityId");
    }

    @Test
    void reclassifyIsPossibleInEveryActiveStateAndNotAfterwards() {
        MaintenanceRequest request = reported();
        request.markAssigned(LATER);
        assertThat(request.reclassify(99L, Priority.HIGH, null, LATER)).isNotEmpty();
        request.start(LATER, null);
        assertThat(request.reclassify(98L, Priority.HIGH, null, LATER)).isNotEmpty();
        request.hold(LATER, "waiting");
        assertThat(request.reclassify(97L, Priority.HIGH, null, LATER)).isNotEmpty();
        request.resume(LATER, null);
        request.resolve(LATER, null);

        assertThat(request.isActive()).isFalse();
        assertThatThrownBy(() -> request.reclassify(96L, Priority.HIGH, null, LATER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void neitherCommandCanMoveTheSpaceOrTheReporter() {
        MaintenanceRequest request = reported();
        request.reviseReport("a", "b", EntryPolicy.ABSENT_ENTRY_ALLOWED, false, null, null, LATER);
        request.reclassify(99L, Priority.LOW, 7L, LATER);

        assertThat(request.getSpaceId()).isEqualTo(2L);
        assertThat(request.getReporterId()).isEqualTo(1L);
        assertThat(java.util.Arrays.stream(MaintenanceRequest.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.equals("setSpaceId") || name.equals("setReporterId")
                        || name.equals("setPriority") || name.equals("setEntryPolicy")))
                .isEmpty();
    }

    private MaintenanceRequest reported() {
        return new MaintenanceRequest(1L, 2L, null, 3L, "Leak", "Ceiling leak", Priority.HIGH,
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, null, null, CREATED);
    }
}
