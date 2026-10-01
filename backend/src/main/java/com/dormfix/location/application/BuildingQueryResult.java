package com.dormfix.location.application;

import java.time.Instant;

public record BuildingQueryResult(Long id, Long dormitoryId, String code, String name,
        boolean active, Instant createdAt, Instant updatedAt) {
}
