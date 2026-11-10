package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.Comment;
import java.util.List;
import java.util.Optional;

/**
 * The resident and staff listings are separate methods on purpose. Neither takes a visibility
 * argument or a flag, so the resident path cannot reach STAFF_ONLY rows through a wrong argument.
 * Deleted comments are excluded from every listing and count.
 */
public interface CommentRepository {
    Comment save(Comment comment);

    Comment saveAndFlush(Comment comment);

    Optional<Comment> findById(Long id);

    /** PUBLIC comments only, ordered createdAt ASC, id ASC. */
    List<Comment> findPublicPage(Long requestId, int page, int size);

    /** Counts PUBLIC comments only, so a resident total never reveals internal remarks. */
    long countPublic(Long requestId);

    /** PUBLIC and STAFF_ONLY comments, ordered createdAt ASC, id ASC. */
    List<Comment> findStaffPage(Long requestId, int page, int size);

    long countStaff(Long requestId);
}
