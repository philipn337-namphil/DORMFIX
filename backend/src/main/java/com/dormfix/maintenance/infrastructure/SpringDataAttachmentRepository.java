package com.dormfix.maintenance.infrastructure;

import com.dormfix.maintenance.domain.Attachment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAttachmentRepository extends JpaRepository<Attachment, Long> {
    List<Attachment> findAllByRequestIdOrderByCreatedAtAscIdAsc(Long requestId);

    long countByRequestId(Long requestId);
}
