package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.MaintenanceRequestSummaryResult;
import java.time.Instant;

/**
 * Listing projection. It deliberately omits description and the entry fields so a bulk listing
 * does not broadcast resident entry-consent data.
 */
public record MaintenanceRequestSummaryResponse(Long id, String requestNumber, String status,
        String priority, Long categoryId, Long spaceId, Long facilityId, String title,
        Long assigneeId, Instant createdAt, Instant updatedAt, Long version) {
    static MaintenanceRequestSummaryResponse from(MaintenanceRequestSummaryResult value) {
        return new MaintenanceRequestSummaryResponse(value.id(), value.requestNumber(),
                value.status(), value.priority(), value.categoryId(), value.spaceId(),
                value.facilityId(), value.title(), value.assigneeId(), value.createdAt(),
                value.updatedAt(), value.version());
    }
}
