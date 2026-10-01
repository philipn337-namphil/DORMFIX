package com.dormfix.catalog.application;

import java.time.Instant;
import java.time.LocalDate;

public record FacilityQueryResult(Long id, Long spaceId, String name, String facilityType,
        String assetCode, String status, LocalDate installedAt, String description,
        Instant createdAt, Instant updatedAt) {
}
