package com.dormfix.location.api;

import com.dormfix.location.application.ResidencePageResult;
import java.util.List;

public record ResidencePageResponse(
        List<ResidenceResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    static ResidencePageResponse from(ResidencePageResult page) {
        return new ResidencePageResponse(
                page.content().stream().map(ResidenceResponse::from).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}
