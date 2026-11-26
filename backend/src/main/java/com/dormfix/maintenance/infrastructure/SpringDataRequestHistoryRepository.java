package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.domain.RequestHistory;
import com.dormfix.maintenance.domain.RequestHistoryEventType;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataRequestHistoryRepository extends JpaRepository<RequestHistory, Long> {
    List<RequestHistory> findAllByRequestIdOrderByCreatedAtAscIdAsc(Long requestId,
            Pageable pageable);

    long countByRequestId(Long requestId);

    List<RequestHistory> findAllByRequestIdAndEventTypeNotOrderByCreatedAtAscIdAsc(Long requestId,
            RequestHistoryEventType excluded, Pageable pageable);

    long countByRequestIdAndEventTypeNot(Long requestId, RequestHistoryEventType excluded);
}
