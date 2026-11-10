package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.domain.Comment;
import com.dormfix.maintenance.domain.CommentVisibility;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findAllByRequestIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(Long requestId,
            Pageable pageable);

    long countByRequestIdAndDeletedAtIsNull(Long requestId);

    List<Comment> findAllByRequestIdAndVisibilityAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
            Long requestId, CommentVisibility visibility, Pageable pageable);

    long countByRequestIdAndVisibilityAndDeletedAtIsNull(Long requestId,
            CommentVisibility visibility);
}
