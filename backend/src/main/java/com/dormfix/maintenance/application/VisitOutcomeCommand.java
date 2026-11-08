package com.dormfix.maintenance.application;

import java.time.Instant;

/** Outcome payload. The actual timestamps are used by complete only. */
public record VisitOutcomeCommand(Long visitId, Instant actualStartedAt, Instant actualEndedAt,
        String note) { }
