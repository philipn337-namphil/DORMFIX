package com.dormfix.maintenance.application;
import java.time.Instant;
public record CreateMaintenanceRequestCommand(Long spaceId, Long facilityId, Long categoryId, String title, String description, String entryPolicy, boolean contactBeforeEntry, Instant preferredVisitStart, Instant preferredVisitEnd) { }
