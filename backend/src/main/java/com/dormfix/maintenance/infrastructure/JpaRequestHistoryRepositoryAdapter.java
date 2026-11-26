package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.RequestHistoryRepository;
import com.dormfix.maintenance.domain.RequestHistory;
import com.dormfix.maintenance.domain.RequestHistoryEventType;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaRequestHistoryRepositoryAdapter implements RequestHistoryRepository {
    // Fixed here rather than passed in, so no caller can widen the resident listing.
    private static final RequestHistoryEventType HIDDEN_FROM_RESIDENT =
            RequestHistoryEventType.COMMENT_DELETED;

    private final SpringDataRequestHistoryRepository delegate;

    JpaRequestHistoryRepositoryAdapter(SpringDataRequestHistoryRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public RequestHistory save(RequestHistory history) {
        return delegate.save(history);
    }

    @Override
    public List<RequestHistory> findStaffPage(Long requestId, int page, int size) {
        return delegate.findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId,
                PageRequest.of(page, size));
    }

    @Override
    public long countStaff(Long requestId) {
        return delegate.countByRequestId(requestId);
    }

    @Override
    public List<RequestHistory> findResidentPage(Long requestId, int page, int size) {
        return delegate.findAllByRequestIdAndEventTypeNotOrderByCreatedAtAscIdAsc(requestId,
                HIDDEN_FROM_RESIDENT, PageRequest.of(page, size));
    }

    @Override
    public long countResident(Long requestId) {
        return delegate.countByRequestIdAndEventTypeNot(requestId, HIDDEN_FROM_RESIDENT);
    }
}
