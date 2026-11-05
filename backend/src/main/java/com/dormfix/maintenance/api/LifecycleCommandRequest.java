package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for start, hold, resume, resolve, close, reopen and reject. It carries no status,
 * assignee or lifecycle timestamp; the actor is the JWT subject. hold and reject additionally
 * require statusReason, which the application service enforces.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record LifecycleCommandRequest(@Size(max = 500) String statusReason,
        @NotNull Long version) { }
