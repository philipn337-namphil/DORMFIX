package com.dormfix.maintenance.application;

import java.util.Set;

/**
 * Listing filters. Every field is optional and narrows within the caller read visibility; none
 * widens it. Statuses is a set because the frozen contract makes status repeatable.
 */
public record MaintenanceRequestQuery(Set<String> statuses, String priority, Long categoryId,
        Long dormitoryId, Long buildingId, Long spaceId, Long assigneeId, int page, int size) { }
