package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.WorkLogRepository;
import com.dormfix.maintenance.domain.WorkLog;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaWorkLogRepositoryAdapter implements WorkLogRepository {
    private final SpringDataWorkLogRepository delegate;

    JpaWorkLogRepositoryAdapter(SpringDataWorkLogRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public WorkLog save(WorkLog workLog) {
        return delegate.save(workLog);
    }

    @Override
    public List<WorkLog> findPageByRequestId(Long requestId, int page, int size) {
        // The ordering is in the derived query name, so the Pageable carries no Sort.
        return delegate.findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId,
                PageRequest.of(page, size));
    }

    @Override
    public long countByRequestId(Long requestId) {
        return delegate.countByRequestId(requestId);
    }
}
