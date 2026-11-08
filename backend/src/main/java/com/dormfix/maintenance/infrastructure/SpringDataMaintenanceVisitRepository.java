package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.domain.MaintenanceVisit;
import com.dormfix.maintenance.domain.VisitStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataMaintenanceVisitRepository extends JpaRepository<MaintenanceVisit, Long> {
    Optional<MaintenanceVisit> findByRequestIdAndStatus(Long requestId, VisitStatus status);

    List<MaintenanceVisit> findAllByRequestIdOrderByScheduledStartDescIdDesc(Long requestId);
}
