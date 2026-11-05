package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.MaintenanceRequest;
import java.util.Optional;

public interface MaintenanceRequestRepository {
    MaintenanceRequest saveAndFlush(MaintenanceRequest request);

    MaintenanceRequest save(MaintenanceRequest request);

    Optional<MaintenanceRequest> findById(Long id);

    /**
     * Listing restricted to the caller visibility. The visibility predicate is applied in the
     * query rather than by filtering a loaded page, so the total count is correct.
     */
    RequestPage findVisiblePage(MaintenanceRequestQuery query, RequestVisibility visibility);

    /** Worker assignment listing. {@code active} selects current rows, otherwise ended rows. */
    RequestPage findAssignedPage(Long workerId, boolean active, int page, int size);
}
