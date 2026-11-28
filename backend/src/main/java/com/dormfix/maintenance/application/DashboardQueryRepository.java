package com.dormfix.maintenance.application;

import com.dormfix.catalog.domain.Priority;
import com.dormfix.maintenance.domain.RequestStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;

/**
 * Dashboard aggregates over the requests whose room resolves to one of the given dormitories.
 * Callers never pass an empty collection; the service answers zeros without querying.
 */
public interface DashboardQueryRepository {
    Map<RequestStatus, Long> countByStatus(Collection<Long> dormitoryIds);

    /** Non-terminal requests only. */
    Map<Priority, Long> countActiveByPriority(Collection<Long> dormitoryIds);

    long countCreatedSince(Collection<Long> dormitoryIds, Instant since);

    long countClosedSince(Collection<Long> dormitoryIds, Instant since);
}
