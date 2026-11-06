package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Assignment command payload. It carries no status, assignedById, timestamp or assignment id;
 * the acting administrator is always the JWT subject.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AssignWorkerRequest(@NotNull Long workerId, @Size(max = 500) String reason,
        @NotNull Long version) { }
