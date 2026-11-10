package com.dormfix.maintenance.application;

import java.time.Instant;

public record CommentResult(Long id, Long requestId, Long authorId, String content,
        String visibility, Instant createdAt) { }
