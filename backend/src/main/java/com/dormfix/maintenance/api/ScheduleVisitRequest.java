package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Scheduling payload. It never accepts workerId, status, an actual timestamp or the consent
 * snapshot: the worker is the request's active assignee and the snapshot comes from the request.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record ScheduleVisitRequest(@NotNull Instant scheduledStart, @NotNull Instant scheduledEnd,
        @Size(max = 500) String note) { }
