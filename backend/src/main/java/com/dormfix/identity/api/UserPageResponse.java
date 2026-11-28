package com.dormfix.identity.api;

import com.dormfix.identity.application.UserPageResult;
import java.util.List;

public record UserPageResponse(List<UserSummaryResponse> content, int page, int size,
        long totalElements, int totalPages) {
    static UserPageResponse from(UserPageResult value) {
        return new UserPageResponse(value.content().stream().map(UserSummaryResponse::from).toList(),
                value.page(), value.size(), value.totalElements(), value.totalPages());
    }
}
