package com.dormfix.catalog.application;

import java.time.LocalDate;

public record CreateFacilityCommand(String name, String facilityType, String assetCode,
        LocalDate installedAt, String description) {
}
