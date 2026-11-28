package com.dormfix.maintenance.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DashboardSummaryResult(List<Long> dormitoryIds, Map<String, Long> statusCounts,
        Map<String, Long> activePriorityCounts, long createdLast7Days, long closedLast7Days,
        Instant generatedAt) { }
