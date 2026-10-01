package com.dormfix.location.api;
import com.dormfix.location.application.ResidenceResult;
import java.time.Instant;
import java.time.LocalDate;
public record ResidenceResponse(Long id, Long residentId, Long roomSpaceId, LocalDate startDate, LocalDate endDate, Instant createdAt) { static ResidenceResponse from(ResidenceResult r) { return new ResidenceResponse(r.id(),r.residentId(),r.roomSpaceId(),r.startDate(),r.endDate(),r.createdAt()); } }
