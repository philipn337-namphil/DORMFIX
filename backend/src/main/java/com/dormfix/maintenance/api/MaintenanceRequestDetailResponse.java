package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.MaintenanceRequestDetailResult;
import java.time.Instant;

/**
 * Detail projection. It embeds no history, visit, work log, comment or attachment collection;
 * each of those has its own path and its own visibility rule.
 */
public record MaintenanceRequestDetailResponse(Long id, String requestNumber, String status,
        String priority, Long categoryId, Long spaceId, Long facilityId, String title,
        Long assigneeId, Instant createdAt, Instant updatedAt, Long version, Long reporterId,
        String description, String entryPolicy, boolean contactBeforeEntry,
        Instant preferredVisitStart, Instant preferredVisitEnd, Long duplicateOfId,
        String statusReason, Instant resolvedAt, Instant closedAt) {
    static MaintenanceRequestDetailResponse from(MaintenanceRequestDetailResult value) {
        return new MaintenanceRequestDetailResponse(value.id(), value.requestNumber(),
                value.status(), value.priority(), value.categoryId(), value.spaceId(),
                value.facilityId(), value.title(), value.assigneeId(), value.createdAt(),
                value.updatedAt(), value.version(), value.reporterId(), value.description(),
                value.entryPolicy(), value.contactBeforeEntry(), value.preferredVisitStart(),
                value.preferredVisitEnd(), value.duplicateOfId(), value.statusReason(),
                value.resolvedAt(), value.closedAt());
    }
}
