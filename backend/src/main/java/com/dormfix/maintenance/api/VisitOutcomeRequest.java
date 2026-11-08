package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** no-access and cancel both record that planned work did not happen, so a note is required. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record VisitOutcomeRequest(@NotBlank @Size(max = 500) String note) { }
