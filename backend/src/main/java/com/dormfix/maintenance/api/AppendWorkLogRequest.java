package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Work log payload. It never accepts workerId, createdAt or id: worker_id is the JWT subject,
 * which is what makes the row a first-person record of who did the work.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AppendWorkLogRequest(@NotBlank String actionType,
        @NotBlank @Size(max = 2000) String description, Long visitId) { }
