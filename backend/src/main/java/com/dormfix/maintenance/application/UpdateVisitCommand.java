package com.dormfix.maintenance.application;

import java.time.Instant;

/**
 * Allowed PATCH fields only. {@code noteSupplied} distinguishes an absent note from an explicit
 * null, so a patch that omits it leaves the stored note alone.
 */
public record UpdateVisitCommand(Long visitId, Instant scheduledStart, Instant scheduledEnd,
        String note, boolean noteSupplied) { }
