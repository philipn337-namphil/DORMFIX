package com.dormfix.catalog.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateFacilityRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 50) String facilityType,
        @Size(max = 100) String assetCode,
        LocalDate installedAt,
        @Size(max = 500) String description) {
}
