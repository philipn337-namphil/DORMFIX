package com.dormfix.maintenance.infrastructure;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
interface SpringDataMaintenanceRequestRepository extends JpaRepository<MaintenanceRequest, Long> { }
