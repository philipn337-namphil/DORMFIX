package com.dormfix.maintenance.infrastructure;
import com.dormfix.maintenance.application.RequestHistoryRepository;
import com.dormfix.maintenance.domain.RequestHistory;
import org.springframework.stereotype.Repository;
@Repository class JpaRequestHistoryRepositoryAdapter implements RequestHistoryRepository { private final SpringDataRequestHistoryRepository delegate; JpaRequestHistoryRepositoryAdapter(SpringDataRequestHistoryRepository delegate){this.delegate=delegate;} public RequestHistory save(RequestHistory history){return delegate.save(history);} }
