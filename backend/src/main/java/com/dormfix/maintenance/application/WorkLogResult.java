package com.dormfix.maintenance.application;

import java.time.Instant;

public record WorkLogResult(Long id, Long requestId, Long visitId, Long workerId,
        String actionType, String description, Instant createdAt) { }
