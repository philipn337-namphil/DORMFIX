package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.RequestHistoryPageResult;
import java.util.List;

public record RequestHistoryPageResponse(List<RequestHistoryEntryResponse> content, int page,
        int size, long totalElements, int totalPages) {
    static RequestHistoryPageResponse from(RequestHistoryPageResult value) {
        return new RequestHistoryPageResponse(
                value.content().stream().map(RequestHistoryEntryResponse::from).toList(),
                value.page(), value.size(), value.totalElements(), value.totalPages());
    }
}
