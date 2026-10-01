package com.dormfix.catalog.application;

import java.time.LocalDate;

public record UpdateFacilityCommand(String name, String facilityType, String assetCode,
        LocalDate installedAt, String description) {
}
