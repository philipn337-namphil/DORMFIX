package com.dormfix.maintenance.application;

import java.util.List;

public record RequestHistoryPageResult(List<RequestHistoryEntryResult> content, int page, int size,
        long totalElements, int totalPages) { }
