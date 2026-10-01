package com.dormfix.location.application;

import java.util.List;

public record ResidencePageResult(
        List<ResidenceResult> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
