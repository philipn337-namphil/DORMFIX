package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.WorkLog;
import java.util.List;

public interface WorkLogRepository {
    WorkLog save(WorkLog workLog);

    /** Ordered createdAt ASC, id ASC: a work log is a narrative that reads oldest first. */
    List<WorkLog> findPageByRequestId(Long requestId, int page, int size);

    long countByRequestId(Long requestId);
}
