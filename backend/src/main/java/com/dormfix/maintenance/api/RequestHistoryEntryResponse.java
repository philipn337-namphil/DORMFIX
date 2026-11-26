package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.RequestHistoryEntryResult;
import java.time.Instant;
import java.util.List;

public record RequestHistoryEntryResponse(Long id, String eventType, Long actorId,
        String previousStatus, String newStatus, String reason, Long workerId,
        Long previousWorkerId, Long visitId, Long duplicateOfId, List<String> changedFields,
        Long commentId, Instant createdAt) {
    static RequestHistoryEntryResponse from(RequestHistoryEntryResult value) {
        return new RequestHistoryEntryResponse(value.id(), value.eventType(), value.actorId(),
                value.previousStatus(), value.newStatus(), value.reason(), value.workerId(),
                value.previousWorkerId(), value.visitId(), value.duplicateOfId(),
                value.changedFields(), value.commentId(), value.createdAt());
    }
}
