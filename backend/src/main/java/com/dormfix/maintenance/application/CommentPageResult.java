package com.dormfix.maintenance.application;

import java.util.List;

public record CommentPageResult(List<CommentResult> content, int page, int size,
        long totalElements, int totalPages) { }
