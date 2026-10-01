package com.dormfix.catalog.api;

import com.dormfix.catalog.application.FacilityQueryResult;
import java.time.Instant;
import java.time.LocalDate;

public record FacilityResponse(Long id, Long spaceId, String name, String facilityType,
        String assetCode, String status, LocalDate installedAt, String description,
        Instant createdAt, Instant updatedAt) {
    public static FacilityResponse from(FacilityQueryResult facility) {
        return new FacilityResponse(facility.id(), facility.spaceId(), facility.name(), facility.facilityType(),
                facility.assetCode(), facility.status(), facility.installedAt(), facility.description(),
                facility.createdAt(), facility.updatedAt());
    }
}
