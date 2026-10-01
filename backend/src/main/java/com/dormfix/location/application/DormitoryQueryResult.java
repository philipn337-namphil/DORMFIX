package com.dormfix.location.application;

import java.time.Instant;

public record DormitoryQueryResult(Long id, String name, String address, String timezone,
        boolean active, Instant createdAt, Instant updatedAt) {
}
