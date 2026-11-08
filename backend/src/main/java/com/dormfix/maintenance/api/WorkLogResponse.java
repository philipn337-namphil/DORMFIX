package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.WorkLogResult;
import java.time.Instant;

public record WorkLogResponse(Long id, Long requestId, Long visitId, Long workerId,
        String actionType, String description, Instant createdAt) {
    static WorkLogResponse from(WorkLogResult value) {
        return new WorkLogResponse(value.id(), value.requestId(), value.visitId(),
                value.workerId(), value.actionType(), value.description(), value.createdAt());
    }
}
