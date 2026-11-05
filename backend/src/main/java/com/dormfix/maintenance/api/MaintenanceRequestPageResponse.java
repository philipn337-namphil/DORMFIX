package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.MaintenanceRequestPageResult;
import java.util.List;

public record MaintenanceRequestPageResponse(List<MaintenanceRequestSummaryResponse> content,
        int page, int size, long totalElements, int totalPages) {
    static MaintenanceRequestPageResponse from(MaintenanceRequestPageResult value) {
        return new MaintenanceRequestPageResponse(
                value.content().stream().map(MaintenanceRequestSummaryResponse::from).toList(),
                value.page(), value.size(), value.totalElements(), value.totalPages());
    }
}
