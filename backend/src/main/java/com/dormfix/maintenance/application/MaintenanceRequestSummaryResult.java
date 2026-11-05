package com.dormfix.maintenance.application;

import java.time.Instant;

public record MaintenanceRequestSummaryResult(Long id, String requestNumber, String status,
        String priority, Long categoryId, Long spaceId, Long facilityId, String title,
        Long assigneeId, Instant createdAt, Instant updatedAt, Long version) { }
