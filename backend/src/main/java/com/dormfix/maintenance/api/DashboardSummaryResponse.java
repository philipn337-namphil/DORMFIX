package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.DashboardSummaryResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResponse(List<Long> dormitoryIds, Map<String, Long> statusCounts,
        Map<String, Long> activePriorityCounts, long createdLast7Days, long closedLast7Days,
        Instant generatedAt) {
    static DashboardSummaryResponse from(DashboardSummaryResult value) {
        return new DashboardSummaryResponse(value.dormitoryIds(), value.statusCounts(),
                value.activePriorityCounts(), value.createdLast7Days(), value.closedLast7Days(),
                value.generatedAt());
    }
}
