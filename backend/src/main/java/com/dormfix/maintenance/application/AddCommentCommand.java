package com.dormfix.maintenance.application;

public record AddCommentCommand(Long requestId, String content, String visibility) { }
