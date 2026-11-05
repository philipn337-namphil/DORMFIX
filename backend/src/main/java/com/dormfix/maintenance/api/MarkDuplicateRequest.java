package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record MarkDuplicateRequest(@NotNull Long duplicateOfId, @Size(max = 500) String statusReason,
        @NotNull Long version) { }
