package com.dormfix.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dormfix.catalog.domain.Priority;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MaintenanceRequestTest {
    @Test void creationStartsReportedAndRequiresAllocatedIdForPublicNumber() {
        MaintenanceRequest request = new MaintenanceRequest(1L, 2L, null, 3L, "Leak", "Ceiling leak", Priority.HIGH,
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, null, null, Instant.parse("2026-10-02T00:00:00Z"));
        assertThat(request.getStatus()).isEqualTo(RequestStatus.REPORTED);
        assertThat(request.getPriority()).isEqualTo(Priority.HIGH);
        assertThatThrownBy(request::assignRequestNumber).isInstanceOf(IllegalStateException.class);
    }

    @Test void assignableFromReportedAndActiveStatesOnly() {
        MaintenanceRequest request = reported();
        assertThat(request.isAssignable()).isTrue();

        RequestStatus previous = request.markAssigned(Instant.parse("2026-11-04T01:00:00Z"));

        assertThat(previous).isEqualTo(RequestStatus.REPORTED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(request.getUpdatedAt()).isEqualTo(Instant.parse("2026-11-04T01:00:00Z"));
        assertThat(request.isAssignable()).isTrue();
    }

    @Test void reassignmentReturnsTheRequestToAssignedAndReportsWhereItCameFrom() {
        MaintenanceRequest request = reported();
        request.markAssigned(Instant.parse("2026-11-04T01:00:00Z"));

        RequestStatus previous = request.markAssigned(Instant.parse("2026-11-04T02:00:00Z"));

        assertThat(previous).isEqualTo(RequestStatus.ASSIGNED);
        assertThat(request.getStatus()).isEqualTo(RequestStatus.ASSIGNED);
    }

    @Test void versionMatchRejectsAStaleOrAbsentValue() {
        MaintenanceRequest request = reported();

        assertThat(request.matchesVersion(null)).isFalse();
        assertThat(request.matchesVersion(0L)).isFalse();
    }

    @Test void thereIsNoPublicStatusSetter() {
        assertThat(java.util.Arrays.stream(MaintenanceRequest.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.equals("setStatus") || name.equals("setVersion"))).isEmpty();
    }

    private MaintenanceRequest reported() {
        return new MaintenanceRequest(1L, 2L, null, 3L, "Leak", "Ceiling leak", Priority.HIGH,
                EntryPolicy.RESIDENT_PRESENT_REQUIRED, true, null, null,
                Instant.parse("2026-11-04T00:00:00Z"));
    }
}
