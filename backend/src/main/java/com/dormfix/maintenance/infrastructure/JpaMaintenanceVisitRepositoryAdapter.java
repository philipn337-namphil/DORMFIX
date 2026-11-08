package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.MaintenanceVisitRepository;
import com.dormfix.maintenance.domain.MaintenanceVisit;
import com.dormfix.maintenance.domain.VisitStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaMaintenanceVisitRepositoryAdapter implements MaintenanceVisitRepository {
    private final SpringDataMaintenanceVisitRepository delegate;

    JpaMaintenanceVisitRepositoryAdapter(SpringDataMaintenanceVisitRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public MaintenanceVisit save(MaintenanceVisit visit) {
        return delegate.save(visit);
    }

    @Override
    public MaintenanceVisit saveAndFlush(MaintenanceVisit visit) {
        return delegate.saveAndFlush(visit);
    }

    @Override
    public Optional<MaintenanceVisit> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public Optional<MaintenanceVisit> findOpenByRequestId(Long requestId) {
        return delegate.findByRequestIdAndStatus(requestId, VisitStatus.SCHEDULED);
    }

    @Override
    public List<MaintenanceVisit> findAllByRequestId(Long requestId) {
        return delegate.findAllByRequestIdOrderByScheduledStartDescIdDesc(requestId);
    }
}
