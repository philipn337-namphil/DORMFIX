package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.MaintenanceAssignment;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface MaintenanceAssignmentRepository {
    MaintenanceAssignment save(MaintenanceAssignment assignment);

    MaintenanceAssignment saveAndFlush(MaintenanceAssignment assignment);

    Optional<MaintenanceAssignment> findActiveByRequestId(Long requestId);

    /** Any assignment row, active or ended, which is what worker read visibility matches. */
    boolean existsByRequestIdAndWorkerId(Long requestId, Long workerId);

    /** Active worker id per request id, resolved in one query to avoid a per-row lookup. */
    Map<Long, Long> findActiveWorkerIds(Collection<Long> requestIds);
}
