package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.CommentPageResult;
import java.util.List;

public record CommentPageResponse(List<CommentResponse> content, int page, int size,
        long totalElements, int totalPages) {
    static CommentPageResponse from(CommentPageResult value) {
        return new CommentPageResponse(
                value.content().stream().map(CommentResponse::from).toList(), value.page(),
                value.size(), value.totalElements(), value.totalPages());
    }
}
