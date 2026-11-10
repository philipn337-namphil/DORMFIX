package com.dormfix.maintenance.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.maintenance.application.RequestRelationshipResolver.RequestRelationship;
import com.dormfix.maintenance.domain.Comment;
import com.dormfix.maintenance.domain.CommentVisibility;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.RequestHistory;
import com.dormfix.maintenance.domain.RequestHistoryEventType;
import com.dormfix.maintenance.domain.RequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Comments. The hard invariant here is that a resident never receives a STAFF_ONLY comment, and it
 * can leak in three places: the rows of a listing, the totals of a listing, and the answer to a
 * delete. Each one is closed separately below.
 */
@Service
public class CommentService {
    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 100;

    private final CommentRepository comments;
    private final MaintenanceRequestRepository requests;
    private final RequestHistoryRepository histories;
    private final RequestRelationshipResolver relationships;
    private final Clock clock;

    public CommentService(CommentRepository comments, MaintenanceRequestRepository requests,
            RequestHistoryRepository histories, RequestRelationshipResolver relationships,
            Clock clock) {
        this.comments = comments;
        this.requests = requests;
        this.histories = histories;
        this.relationships = relationships;
        this.clock = clock;
    }

    @Transactional
    public CommentResult add(AddCommentCommand command, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(command.requestId())
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(command.requestId()));
        RequestRelationship relationship = relationships.resolve(request, actorId, roles);
        if (!relationship.canRead()) {
            throw new MaintenanceRequestNotFoundException(command.requestId());
        }
        CommentVisibility visibility = visibility(command.visibility());

        // Authorization before the state check.
        if (!relationship.staffWriter()) {
            if (!relationship.reporter()) {
                // Readable but not writable: a former assignee.
                throw new WorkerNotAssignedException();
            }
            if (visibility == CommentVisibility.STAFF_ONLY) {
                throw new DormitoryScopeAccessDeniedException();
            }
        }
        if (request.isIn(RequestStatus.CLOSED, RequestStatus.REJECTED, RequestStatus.DUPLICATE)) {
            throw new MaintenanceRequestConflictException(
                    "The request is terminal and cannot take a comment.");
        }
        Comment saved = comments.save(new Comment(request.getId(), actorId, command.content(),
                visibility, Instant.now(clock)));
        return result(saved);
    }

    @Transactional(readOnly = true)
    public CommentPageResult listForRequest(Long requestId, int page, int size, Long actorId,
            Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        if (page < 0 || size < MIN_SIZE || size > MAX_SIZE) {
            throw new InvalidMaintenanceQueryException("Invalid pagination.");
        }
        MaintenanceRequest request = requests.findById(requestId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(requestId));
        RequestRelationship relationship = relationships.resolve(request, actorId, roles);
        if (!relationship.canRead()) {
            throw new MaintenanceRequestNotFoundException(requestId);
        }
        // Two separate queries, and two separate counts. The resident total counts PUBLIC rows
        // only, otherwise the number of internal remarks would leak even with the rows hidden.
        List<Comment> rows;
        long total;
        if (relationship.staffReader()) {
            rows = comments.findStaffPage(requestId, page, size);
            total = comments.countStaff(requestId);
        } else {
            rows = comments.findPublicPage(requestId, page, size);
            total = comments.countPublic(requestId);
        }
        List<CommentResult> content = new ArrayList<>(rows.size());
        for (Comment comment : rows) {
            content.add(result(comment));
        }
        int totalPages = (int) Math.ceil((double) total / size);
        return new CommentPageResult(List.copyOf(content), page, size, total, totalPages);
    }

    @Transactional
    public void delete(Long commentId, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        Comment comment = comments.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        if (comment.isDeleted()) {
            throw new CommentNotFoundException(commentId);
        }
        MaintenanceRequest request = requests.findById(comment.getRequestId())
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        RequestRelationship relationship = relationships.resolve(request, actorId, roles);

        // A comment the caller cannot read answers exactly like a missing one. Comment ids are
        // sequential, so a 403 here would tell a resident that an internal remark exists.
        boolean readable = relationship.staffReader()
                || relationship.reporter() && comment.isPublic();
        if (!readable) {
            throw new CommentNotFoundException(commentId);
        }
        boolean ownWithAuthority = comment.getAuthorId().equals(actorId)
                && (relationship.staffWriter() || relationship.reporter());
        if (!relationship.administrator() && !ownWithAuthority) {
            throw new DormitoryScopeAccessDeniedException();
        }

        Instant now = Instant.now(clock);
        comment.softDelete(now);
        comments.saveAndFlush(comment);
        // The comment row has no column for who deleted it, so the audit row records the actor.
        // It carries the id only, never the content.
        histories.save(new RequestHistory(request.getId(), actorId,
                RequestHistoryEventType.COMMENT_DELETED, request.getStatus(), request.getStatus(),
                now, "{\"commentId\":" + comment.getId() + "}"));
    }

    private CommentVisibility visibility(String value) {
        try {
            return CommentVisibility.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new InvalidMaintenanceQueryException("Invalid comment visibility.");
        }
    }

    private CommentResult result(Comment comment) {
        return new CommentResult(comment.getId(), comment.getRequestId(), comment.getAuthorId(),
                comment.getContent(), comment.getVisibility().name(), comment.getCreatedAt());
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
