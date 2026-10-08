package com.dormfix.maintenance.api;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateMaintenanceRequest(@NotNull Long spaceId, Long facilityId, @NotNull Long categoryId, @NotBlank @Size(max=150) String title, @NotBlank String description, @NotBlank String entryPolicy, boolean contactBeforeEntry, Instant preferredVisitStart, Instant preferredVisitEnd) { }
