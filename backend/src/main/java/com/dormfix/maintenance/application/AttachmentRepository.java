package com.dormfix.maintenance.application;

import com.dormfix.maintenance.domain.Attachment;
import java.util.List;
import java.util.Optional;

public interface AttachmentRepository {
    Attachment saveAndFlush(Attachment attachment);

    Optional<Attachment> findById(Long id);

    /** Ordered createdAt ASC, id ASC. Bounded by the per-request attachment limit. */
    List<Attachment> findAllByRequestId(Long requestId);

    long countByRequestId(Long requestId);
}
