package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.CommentResult;
import java.time.Instant;

public record CommentResponse(Long id, Long requestId, Long authorId, String content,
        String visibility, Instant createdAt) {
    static CommentResponse from(CommentResult value) {
        return new CommentResponse(value.id(), value.requestId(), value.authorId(),
                value.content(), value.visibility(), value.createdAt());
    }
}
