package com.dormfix.maintenance.infrastructure;
import com.dormfix.maintenance.application.MaintenanceRequestRepository;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import org.springframework.stereotype.Repository;
@Repository class JpaMaintenanceRequestRepositoryAdapter implements MaintenanceRequestRepository { private final SpringDataMaintenanceRequestRepository delegate; JpaMaintenanceRequestRepositoryAdapter(SpringDataMaintenanceRequestRepository delegate){this.delegate=delegate;} public MaintenanceRequest saveAndFlush(MaintenanceRequest request){return delegate.saveAndFlush(request);} public MaintenanceRequest save(MaintenanceRequest request){return delegate.save(request);} }
