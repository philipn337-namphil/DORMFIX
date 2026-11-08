package com.dormfix.maintenance.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * The worker supplies both actual timestamps. The completion call does not happen at the moment the
 * work ends, so a server clock would record when the report was filed instead.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record CompleteVisitRequest(@NotNull Instant actualStartedAt, @NotNull Instant actualEndedAt,
        @Size(max = 500) String note) { }
