package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.domain.WorkLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataWorkLogRepository extends JpaRepository<WorkLog, Long> {
    List<WorkLog> findAllByRequestIdOrderByCreatedAtAscIdAsc(Long requestId, Pageable pageable);

    long countByRequestId(Long requestId);
}
