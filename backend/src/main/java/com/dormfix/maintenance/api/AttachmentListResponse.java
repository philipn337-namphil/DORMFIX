package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.AttachmentResult;
import java.util.List;

/** Ordered createdAt ASC, id ASC. Not paginated: the per-request limit bounds it. */
public record AttachmentListResponse(List<AttachmentResponse> content) {
    static AttachmentListResponse from(List<AttachmentResult> values) {
        return new AttachmentListResponse(values.stream().map(AttachmentResponse::from).toList());
    }
}
