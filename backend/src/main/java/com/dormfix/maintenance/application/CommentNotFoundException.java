package com.dormfix.maintenance.application;

/**
 * Raised when a comment is missing, already deleted, or not readable by the caller. All three
 * answer 404 COMMENT_NOT_FOUND so the delete path never discloses a STAFF_ONLY comment.
 */
public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(Long id) {
        super("Comment was not found: " + id);
    }
}
