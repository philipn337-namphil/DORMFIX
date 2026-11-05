package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.domain.MaintenanceAssignment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataMaintenanceAssignmentRepository
        extends JpaRepository<MaintenanceAssignment, Long> {
    Optional<MaintenanceAssignment> findByRequestIdAndUnassignedAtIsNull(Long requestId);

    boolean existsByRequestIdAndWorkerId(Long requestId, Long workerId);

    @Query("SELECT a FROM MaintenanceAssignment a WHERE a.requestId IN :requestIds"
            + " AND a.unassignedAt IS NULL")
    List<MaintenanceAssignment> findActiveByRequestIds(
            @Param("requestIds") Collection<Long> requestIds);
}
