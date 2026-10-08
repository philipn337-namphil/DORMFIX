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
}
