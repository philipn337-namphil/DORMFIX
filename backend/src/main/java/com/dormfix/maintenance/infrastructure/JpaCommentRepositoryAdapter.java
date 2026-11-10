package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.CommentRepository;
import com.dormfix.maintenance.domain.Comment;
import com.dormfix.maintenance.domain.CommentVisibility;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
class JpaCommentRepositoryAdapter implements CommentRepository {
    private final SpringDataCommentRepository delegate;

    JpaCommentRepositoryAdapter(SpringDataCommentRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Comment save(Comment comment) {
        return delegate.save(comment);
    }

    @Override
    public Comment saveAndFlush(Comment comment) {
        return delegate.saveAndFlush(comment);
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return delegate.findById(id);
    }

    // PUBLIC is fixed here rather than passed in, so no caller can widen the resident listing.
    @Override
    public List<Comment> findPublicPage(Long requestId, int page, int size) {
        return delegate.findAllByRequestIdAndVisibilityAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
                requestId, CommentVisibility.PUBLIC, PageRequest.of(page, size));
    }

    @Override
    public long countPublic(Long requestId) {
        return delegate.countByRequestIdAndVisibilityAndDeletedAtIsNull(requestId,
                CommentVisibility.PUBLIC);
    }

    @Override
    public List<Comment> findStaffPage(Long requestId, int page, int size) {
        return delegate.findAllByRequestIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(requestId,
                PageRequest.of(page, size));
    }

    @Override
    public long countStaff(Long requestId) {
        return delegate.countByRequestIdAndDeletedAtIsNull(requestId);
    }
}
