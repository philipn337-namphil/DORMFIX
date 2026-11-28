package com.dormfix.maintenance.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.dormfix.catalog.domain.Priority;
import com.dormfix.maintenance.domain.RequestStatus;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DashboardCountsTest {
    @Test
    void everyStatusIsAKeyInEnumOrderAndMissingOnesAreZero() {
        Map<String, Long> filled = DashboardCounts.statuses(Map.of(RequestStatus.ASSIGNED, 4L,
                RequestStatus.CLOSED, 2L));

        assertThat(filled.keySet()).containsExactly("REPORTED", "ASSIGNED", "IN_PROGRESS",
                "ON_HOLD", "RESOLVED", "CLOSED", "REJECTED", "DUPLICATE");
        assertThat(filled).containsEntry("ASSIGNED", 4L).containsEntry("CLOSED", 2L)
                .containsEntry("REPORTED", 0L);
    }

    @Test
    void everyPriorityIsAKeyAndMissingOnesAreZero() {
        Map<String, Long> filled = DashboardCounts.priorities(Map.of(Priority.EMERGENCY, 1L));

        assertThat(filled.keySet()).containsExactly("LOW", "NORMAL", "HIGH", "EMERGENCY");
        assertThat(filled).containsEntry("EMERGENCY", 1L).containsEntry("LOW", 0L);
    }
}
