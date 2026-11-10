package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.MaintenanceRequest;
import java.time.Instant;

public record MaintenanceRequestDetailResult(Long id, String requestNumber, String status,
        String priority, Long categoryId, Long spaceId, Long facilityId, String title,
        Long assigneeId, Instant createdAt, Instant updatedAt, Long version, Long reporterId,
        String description, String entryPolicy, boolean contactBeforeEntry,
        Instant preferredVisitStart, Instant preferredVisitEnd, Long duplicateOfId,
        String statusReason, Instant resolvedAt, Instant closedAt) {

    /** Single mapping used by the detail read and by the metadata PATCH response. */
    public static MaintenanceRequestDetailResult of(MaintenanceRequest request, Long assigneeId) {
        return new MaintenanceRequestDetailResult(request.getId(), request.getRequestNumber(),
                request.getStatus().name(), request.getPriority().name(), request.getCategoryId(),
                request.getSpaceId(), request.getFacilityId(), request.getTitle(), assigneeId,
                request.getCreatedAt(), request.getUpdatedAt(), request.getVersion(),
                request.getReporterId(), request.getDescription(), request.getEntryPolicy().name(),
                request.isContactBeforeEntry(), request.getPreferredVisitStart(),
                request.getPreferredVisitEnd(), request.getDuplicateOfId(),
                request.getStatusReason(), request.getResolvedAt(), request.getClosedAt());
    }
}
