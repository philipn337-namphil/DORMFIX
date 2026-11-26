package com.dormfix.maintenance.application;

import java.time.Instant;
import java.util.List;

public record RequestHistoryEntryResult(Long id, String eventType, Long actorId,
        String previousStatus, String newStatus, String reason, Long workerId,
        Long previousWorkerId, Long visitId, Long duplicateOfId, List<String> changedFields,
        Long commentId, Instant createdAt) { }
