package com.dormfix.maintenance.application;

import java.time.Instant;

public record ScheduleVisitCommand(Long requestId, Instant scheduledStart, Instant scheduledEnd,
        String note) { }
