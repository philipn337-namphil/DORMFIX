package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.VisitResult;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Visit projection. There is deliberately no general {@code from} factory: {@code note} is
 * free-text staff content, so every caller must choose {@link #forStaff} or {@link #forResident}
 * and cannot leak it by reaching for a default mapping. A resident response omits the key entirely
 * rather than sending it as null.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record VisitResponse(Long id, Long requestId, Long workerId, Long createdById,
        Instant scheduledStart, Instant scheduledEnd, Instant actualStartedAt,
        Instant actualEndedAt, String status, String entryPolicySnapshot,
        boolean contactBeforeEntry, String note, Instant createdAt, Instant updatedAt) {

    static VisitResponse forStaff(VisitResult value) {
        return of(value, value.note());
    }

    /** The frozen matrix grants a resident the visit schedule, not the staff record. */
    static VisitResponse forResident(VisitResult value) {
        return of(value, null);
    }

    private static VisitResponse of(VisitResult value, String note) {
        return new VisitResponse(value.id(), value.requestId(), value.workerId(),
                value.createdById(), value.scheduledStart(), value.scheduledEnd(),
                value.actualStartedAt(), value.actualEndedAt(), value.status(),
                value.entryPolicySnapshot(), value.contactBeforeEntry(), note, value.createdAt(),
                value.updatedAt());
    }
}
