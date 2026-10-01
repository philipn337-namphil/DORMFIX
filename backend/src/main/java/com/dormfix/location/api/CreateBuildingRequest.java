package com.dormfix.location.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateBuildingRequest(
        @NotBlank @Size(max = 30) String code,
        @NotBlank @Size(max = 100) String name) {
}
