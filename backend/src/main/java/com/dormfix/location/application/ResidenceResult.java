package com.dormfix.location.application;
import java.time.Instant; import java.time.LocalDate;
public record ResidenceResult(Long id,Long residentId,Long roomSpaceId,LocalDate startDate,LocalDate endDate,Instant createdAt) { }
