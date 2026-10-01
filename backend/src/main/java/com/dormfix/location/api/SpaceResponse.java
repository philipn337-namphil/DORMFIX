package com.dormfix.location.api;

import com.dormfix.location.application.SpaceQueryResult;
import java.time.Instant;

public record SpaceResponse(Long id, Long buildingId, String code, String name, String type,
        int floor, String description, boolean active, Instant createdAt, Instant updatedAt) {
    public static SpaceResponse from(SpaceQueryResult space) {
        return new SpaceResponse(space.id(), space.buildingId(), space.code(), space.name(), space.type(),
                space.floor(), space.description(), space.active(), space.createdAt(), space.updatedAt());
    }
}
