package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.application.AttachmentRepository;
import com.dormfix.maintenance.domain.Attachment;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class JpaAttachmentRepositoryAdapter implements AttachmentRepository {
    private final SpringDataAttachmentRepository delegate;

    JpaAttachmentRepositoryAdapter(SpringDataAttachmentRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public Attachment saveAndFlush(Attachment attachment) {
        return delegate.saveAndFlush(attachment);
    }

    @Override
    public Optional<Attachment> findById(Long id) {
        return delegate.findById(id);
    }

    @Override
    public List<Attachment> findAllByRequestId(Long requestId) {
        return delegate.findAllByRequestIdOrderByCreatedAtAscIdAsc(requestId);
    }

    @Override
    public long countByRequestId(Long requestId) {
        return delegate.countByRequestId(requestId);
    }
}
