package com.dormfix.location.api;

import com.dormfix.location.application.BuildingQueryResult;
import java.time.Instant;

public record BuildingResponse(Long id, Long dormitoryId, String code, String name,
        boolean active, Instant createdAt, Instant updatedAt) {
    public static BuildingResponse from(BuildingQueryResult building) {
        return new BuildingResponse(building.id(), building.dormitoryId(), building.code(), building.name(),
                building.active(), building.createdAt(), building.updatedAt());
    }
}
