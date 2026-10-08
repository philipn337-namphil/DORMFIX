package com.dormfix.maintenance.infrastructure;
import com.dormfix.maintenance.domain.RequestHistory;
import org.springframework.data.jpa.repository.JpaRepository;
interface SpringDataRequestHistoryRepository extends JpaRepository<RequestHistory, Long> { }
