package com.dormfix.maintenance.application;

import java.time.Instant;

/**
 * Full visit projection for staff. {@code note} is free-text staff content, so the API layer
 * must omit it for a RESIDENT reader.
 */
public record VisitResult(Long id, Long requestId, Long workerId, Long createdById,
        Instant scheduledStart, Instant scheduledEnd, Instant actualStartedAt,
        Instant actualEndedAt, String status, String entryPolicySnapshot,
        boolean contactBeforeEntry, String note, Instant createdAt, Instant updatedAt) { }
