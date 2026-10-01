package com.dormfix.location.api;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateResidenceRequest(
        @NotNull Long residentId,
        @NotNull Long roomSpaceId,
        @NotNull LocalDate startDate) {
}
