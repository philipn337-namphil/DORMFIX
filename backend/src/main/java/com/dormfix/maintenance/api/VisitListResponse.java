package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.VisitResult;
import java.util.List;
import java.util.Set;

/**
 * Visit list ordered scheduledStart DESC, id DESC. Not paginated: visits per request are bounded
 * by the one-open-visit rule.
 */
public record VisitListResponse(List<VisitResponse> content) {

    /**
     * Staff readers are callers holding WORKER, ADMIN or SUPER_ADMIN. A caller who only holds
     * RESIDENT reads the schedule without the staff note.
     */
    static VisitListResponse from(List<VisitResult> results, Set<String> roleNames) {
        boolean staff = roleNames.contains("WORKER") || roleNames.contains("ADMIN")
                || roleNames.contains("SUPER_ADMIN");
        return new VisitListResponse(results.stream()
                .map(value -> staff ? VisitResponse.forStaff(value)
                        : VisitResponse.forResident(value))
                .toList());
    }
}
