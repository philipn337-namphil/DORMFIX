package com.dormfix.maintenance.application;

import java.time.Instant;

public record MaintenanceRequestDetailResult(Long id, String requestNumber, String status,
        String priority, Long categoryId, Long spaceId, Long facilityId, String title,
        Long assigneeId, Instant createdAt, Instant updatedAt, Long version, Long reporterId,
        String description, String entryPolicy, boolean contactBeforeEntry,
        Instant preferredVisitStart, Instant preferredVisitEnd, Long duplicateOfId,
        String statusReason, Instant resolvedAt, Instant closedAt) { }
