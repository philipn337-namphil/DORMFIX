package com.dormfix.maintenance.application;

import com.dormfix.catalog.domain.Priority;
import com.dormfix.maintenance.domain.RequestStatus;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Every enum value is a key, in declaration order, so a client never has to guess a missing 0. */
final class DashboardCounts {
    private DashboardCounts() { }

    static Map<String, Long> statuses(Map<RequestStatus, Long> counted) {
        Map<String, Long> filled = new LinkedHashMap<>();
        for (RequestStatus status : RequestStatus.values()) {
            filled.put(status.name(), counted.getOrDefault(status, 0L));
        }
        return Collections.unmodifiableMap(filled);
    }

    static Map<String, Long> priorities(Map<Priority, Long> counted) {
        Map<String, Long> filled = new LinkedHashMap<>();
        for (Priority priority : Priority.values()) {
            filled.put(priority.name(), counted.getOrDefault(priority, 0L));
        }
        return Collections.unmodifiableMap(filled);
    }
}
