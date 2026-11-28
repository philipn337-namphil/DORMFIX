package com.dormfix.identity.api;

import com.dormfix.identity.application.UserSummaryResult;
import java.util.List;

public record UserSummaryResponse(Long id, String name, String email, List<String> roles,
        String status) {
    static UserSummaryResponse from(UserSummaryResult value) {
        return new UserSummaryResponse(value.id(), value.name(), value.email(), value.roles(),
                value.status());
    }
}
