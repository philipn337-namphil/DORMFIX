package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.WorkLogPageResult;
import java.util.List;

public record WorkLogPageResponse(List<WorkLogResponse> content, int page, int size,
        long totalElements, int totalPages) {
    static WorkLogPageResponse from(WorkLogPageResult value) {
        return new WorkLogPageResponse(
                value.content().stream().map(WorkLogResponse::from).toList(), value.page(),
                value.size(), value.totalElements(), value.totalPages());
    }
}
