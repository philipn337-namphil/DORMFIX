package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.MaintenanceRequestQuery;
import com.dormfix.maintenance.application.MaintenanceRequestRepository;
import com.dormfix.maintenance.application.RequestPage;
import com.dormfix.maintenance.application.RequestVisibility;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaMaintenanceRequestRepositoryAdapter implements MaintenanceRequestRepository {
    /**
     * A native IN list cannot take an empty collection, so an absent status filter passes a value
     * that no status can equal. The companion count parameter is what actually disables the filter.
     */
    private static final Set<String> NO_STATUS = Set.of("");

    private final SpringDataMaintenanceRequestRepository delegate;

    JpaMaintenanceRequestRepositoryAdapter(SpringDataMaintenanceRequestRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public MaintenanceRequest saveAndFlush(MaintenanceRequest request) {
        return delegate.saveAndFlush(request);
    }

    @Override
    public MaintenanceRequest save(MaintenanceRequest request) {
        return delegate.save(request);
    }

    @Override
    public Optional<MaintenanceRequest> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public RequestPage findVisiblePage(MaintenanceRequestQuery query,
            RequestVisibility visibility) {
        Set<String> statuses = query.statuses() == null || query.statuses().isEmpty() ? NO_STATUS
                : query.statuses();
        int statusCount = statuses == NO_STATUS ? 0 : statuses.size();
        Page<MaintenanceRequest> page = delegate.findVisiblePage(visibility.actorId(),
                visibility.allScopes(), visibility.resident(), visibility.worker(),
                visibility.dormitoryIds(), statuses, statusCount, query.priority(),
                query.categoryId(), query.dormitoryId(), query.buildingId(), query.spaceId(),
                query.assigneeId(), unsorted(query.page(), query.size()));
        return new RequestPage(page.getContent(), page.getTotalElements());
    }

    @Override
    public RequestPage findAssignedPage(Long workerId, boolean active, int page, int size) {
        Page<MaintenanceRequest> result =
                delegate.findAssignedPage(workerId, active, unsorted(page, size));
        return new RequestPage(result.getContent(), result.getTotalElements());
    }

    /**
     * The ordering lives in the native statement. A sorted Pageable would make Spring Data append
     * Java property names to that SQL, which is the defect the Residence slice already had to fix.
     */
    private PageRequest unsorted(int page, int size) {
        return PageRequest.of(page, size);
    }
}
