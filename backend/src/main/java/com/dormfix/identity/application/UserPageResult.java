package com.dormfix.identity.application;

import java.util.List;

public record UserPageResult(List<UserSummaryResult> content, int page, int size,
        long totalElements, int totalPages) { }
