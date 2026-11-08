package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.MaintenanceVisit;
import java.util.List;
import java.util.Optional;

public interface MaintenanceVisitRepository {
    MaintenanceVisit save(MaintenanceVisit visit);

    MaintenanceVisit saveAndFlush(MaintenanceVisit visit);

    Optional<MaintenanceVisit> findById(Long id);

    Optional<MaintenanceVisit> findOpenByRequestId(Long requestId);

    /** Ordered scheduledStart DESC, id DESC. Not paginated: visits per request are few. */
    List<MaintenanceVisit> findAllByRequestId(Long requestId);
}
