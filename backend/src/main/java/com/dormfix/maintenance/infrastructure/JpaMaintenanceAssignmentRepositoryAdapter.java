package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.MaintenanceAssignmentRepository;
import com.dormfix.maintenance.domain.MaintenanceAssignment;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaMaintenanceAssignmentRepositoryAdapter implements MaintenanceAssignmentRepository {
    private final SpringDataMaintenanceAssignmentRepository delegate;

    JpaMaintenanceAssignmentRepositoryAdapter(
            SpringDataMaintenanceAssignmentRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public MaintenanceAssignment save(MaintenanceAssignment assignment) {
        return delegate.save(assignment);
    }

    @Override
    public MaintenanceAssignment saveAndFlush(MaintenanceAssignment assignment) {
        return delegate.saveAndFlush(assignment);
    }

    @Override
    public Optional<MaintenanceAssignment> findActiveByRequestId(Long requestId) {
        return delegate.findByRequestIdAndUnassignedAtIsNull(requestId);
    }

    @Override
    public boolean existsByRequestIdAndWorkerId(Long requestId, Long workerId) {
        return delegate.existsByRequestIdAndWorkerId(requestId, workerId);
    }

    @Override
    public Map<Long, Long> findActiveWorkerIds(Collection<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> active = new HashMap<>();
        for (MaintenanceAssignment assignment : delegate.findActiveByRequestIds(requestIds)) {
            active.put(assignment.getRequestId(), assignment.getWorkerId());
        }
        return Map.copyOf(active);
    }
}
