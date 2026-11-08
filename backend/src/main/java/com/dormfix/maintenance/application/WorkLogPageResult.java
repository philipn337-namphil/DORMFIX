package com.dormfix.maintenance.application;

import java.util.List;

public record WorkLogPageResult(List<WorkLogResult> content, int page, int size,
        long totalElements, int totalPages) { }
