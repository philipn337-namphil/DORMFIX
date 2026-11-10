package com.dormfix.maintenance.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.maintenance.application.RequestRelationshipResolver.RequestRelationship;
import com.dormfix.maintenance.domain.Attachment;
import com.dormfix.maintenance.domain.AttachmentType;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.RequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every database step of the attachment flow, each in its own short transaction. It never calls
 * storage: the orchestrating service does that between these methods, so no storage call runs
 * inside a transaction.
 */
@Service
public class AttachmentAccess {
    private final AttachmentRepository attachments;
    private final MaintenanceRequestRepository requests;
    private final RequestRelationshipResolver relationships;
    private final AttachmentPolicy policy;
    private final Clock clock;

    public AttachmentAccess(AttachmentRepository attachments,
            MaintenanceRequestRepository requests, RequestRelationshipResolver relationships,
            AttachmentPolicy policy, Clock clock) {
        this.attachments = attachments;
        this.requests = requests;
        this.relationships = relationships;
        this.policy = policy;
        this.clock = clock;
    }

    /**
     * Visibility, then type authority, then request state, then the per-request limit. Used before
     * both the presign and the registration.
     */
    @Transactional(readOnly = true)
    public void authorizeUpload(Long requestId, AttachmentType type, Long actorId,
            Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(requestId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(requestId));
        RequestRelationship relationship = relationships.resolve(request, actorId, roles);
        if (!relationship.canRead()) {
            throw new MaintenanceRequestNotFoundException(requestId);
        }
        if (!allowedTypes(relationship).contains(type)) {
            if (!relationship.staffWriter() && !relationship.reporter()) {
                // Readable but not writable: a former assignee.
                throw new WorkerNotAssignedException();
            }
            throw new DormitoryScopeAccessDeniedException();
        }
        requireOpen(request);
        requireRoom(requestId);
    }

    /**
     * The write step. The request may have turned terminal, or filled up, between the presign and
     * now, because the storage calls in between hold no transaction, so both are checked again.
     */
    @Transactional
    public AttachmentResult register(Long requestId, Long uploaderId, AttachmentType type,
            String objectKey, String originalFilename, long fileSize, String contentType) {
        MaintenanceRequest request = requests.findById(requestId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(requestId));
        requireOpen(request);
        requireRoom(requestId);
        try {
            return result(attachments.saveAndFlush(new Attachment(requestId, uploaderId, type,
                    objectKey, originalFilename, contentType, fileSize, Instant.now(clock))));
        } catch (DataIntegrityViolationException error) {
            // uq_attachment_object_key: this object is already registered.
            throw new MaintenanceRequestConflictException(
                    "The uploaded object is already registered.");
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResult> list(Long requestId, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(requestId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(requestId));
        if (!relationships.resolve(request, actorId, roles).canRead()) {
            throw new MaintenanceRequestNotFoundException(requestId);
        }
        List<AttachmentResult> results = new ArrayList<>();
        for (Attachment attachment : attachments.findAllByRequestId(requestId)) {
            results.add(result(attachment));
        }
        return List.copyOf(results);
    }

    /**
     * Resolves what a download needs. An attachment on a request the caller cannot read answers
     * like a missing one, because attachment ids are sequential.
     */
    @Transactional(readOnly = true)
    public DownloadGrant authorizeDownload(Long attachmentId, Long actorId,
            Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        Attachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        MaintenanceRequest request = requests.findById(attachment.getRequestId())
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        if (!relationships.resolve(request, actorId, roles).canRead()) {
            throw new AttachmentNotFoundException(attachmentId);
        }
        return new DownloadGrant(attachment.getObjectKey(), attachment.getOriginalFilename());
    }

    /** The object key leaves this class only inside a grant, never inside a response. */
    public record DownloadGrant(String objectKey, String originalFilename) { }

    private Set<AttachmentType> allowedTypes(RequestRelationship relationship) {
        Set<AttachmentType> allowed = new LinkedHashSet<>();
        if (relationship.reporter()) {
            allowed.add(AttachmentType.REPORT);
            allowed.add(AttachmentType.OTHER);
        }
        if (relationship.currentAssignee()) {
            allowed.add(AttachmentType.BEFORE_REPAIR);
            allowed.add(AttachmentType.AFTER_REPAIR);
            allowed.add(AttachmentType.OTHER);
        }
        if (relationship.administrator()) {
            allowed.add(AttachmentType.OTHER);
        }
        return allowed;
    }

    private void requireOpen(MaintenanceRequest request) {
        if (request.isIn(RequestStatus.CLOSED, RequestStatus.REJECTED, RequestStatus.DUPLICATE)) {
            throw new MaintenanceRequestConflictException(
                    "The request is terminal and cannot take an attachment.");
        }
    }

    private void requireRoom(Long requestId) {
        if (attachments.countByRequestId(requestId) >= policy.maxAttachmentsPerRequest()) {
            throw new MaintenanceRequestConflictException(
                    "The request has reached its attachment limit.");
        }
    }

    private AttachmentResult result(Attachment attachment) {
        return new AttachmentResult(attachment.getId(), attachment.getRequestId(),
                attachment.getUploaderId(), attachment.getType().name(),
                attachment.getOriginalFilename(), attachment.getContentType(),
                attachment.getFileSize(), attachment.getCreatedAt());
    }

    private Set<Role> roles(Set<String> names) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String name : names) {
            try {
                roles.add(Role.valueOf(name));
            } catch (IllegalArgumentException error) {
                throw new DormitoryScopeAccessDeniedException();
            }
        }
        if (roles.isEmpty()) {
            throw new DormitoryScopeAccessDeniedException();
        }
        return roles;
    }
}
