package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/** Allow-listed PATCH fields. Status, worker, actual times and the consent snapshot are immutable here. */
@JsonIgnoreProperties(ignoreUnknown = false)
public record UpdateVisitRequest(Instant scheduledStart, Instant scheduledEnd,
        @Size(max = 500) String note) { }
