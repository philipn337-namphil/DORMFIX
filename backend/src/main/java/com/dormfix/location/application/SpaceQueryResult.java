package com.dormfix.location.application;

import java.time.Instant;

public record SpaceQueryResult(Long id, Long buildingId, String code, String name, String type,
        int floor, String description, boolean active, Instant createdAt, Instant updatedAt) {
}
