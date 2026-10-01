package com.dormfix.location.api;

import com.dormfix.location.application.DormitoryQueryResult;
import java.time.Instant;

public record DormitoryResponse(Long id, String name, String address, String timezone,
        boolean active, Instant createdAt, Instant updatedAt) {
    public static DormitoryResponse from(DormitoryQueryResult dormitory) {
        return new DormitoryResponse(dormitory.id(), dormitory.name(), dormitory.address(),
                dormitory.timezone(), dormitory.active(), dormitory.createdAt(), dormitory.updatedAt());
    }
}
