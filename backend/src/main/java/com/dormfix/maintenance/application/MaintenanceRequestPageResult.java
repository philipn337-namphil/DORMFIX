package com.dormfix.maintenance.application;

import java.util.List;

public record MaintenanceRequestPageResult(List<MaintenanceRequestSummaryResult> content, int page,
        int size, long totalElements, int totalPages) { }
