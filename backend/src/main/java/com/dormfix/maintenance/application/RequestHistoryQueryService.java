package com.dormfix.maintenance.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.maintenance.application.RequestRelationshipResolver.RequestRelationship;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.RequestHistory;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the append-only RequestHistory of one request (ADR-028). Visibility is the request read
 * visibility. A reader without a staff relationship never receives COMMENT_DELETED rows, in the
 * rows or in the totals, because their commentId would disclose that an internal remark existed.
 */
@Service
public class RequestHistoryQueryService {
    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 100;

    private final RequestHistoryRepository histories;
    private final MaintenanceRequestRepository requests;
    private final RequestRelationshipResolver relationships;

    public RequestHistoryQueryService(RequestHistoryRepository histories,
            MaintenanceRequestRepository requests, RequestRelationshipResolver relationships) {
        this.histories = histories;
        this.requests = requests;
        this.relationships = relationships;
    }

    @Transactional(readOnly = true)
    public RequestHistoryPageResult listForRequest(Long requestId, int page, int size,
            Long actorId, Set<String> roleNames) {
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
        List<RequestHistory> rows;
        long total;
        if (relationship.staffReader()) {
            rows = histories.findStaffPage(requestId, page, size);
            total = histories.countStaff(requestId);
        } else {
            rows = histories.findResidentPage(requestId, page, size);
            total = histories.countResident(requestId);
        }
        List<RequestHistoryEntryResult> content = new ArrayList<>(rows.size());
        for (RequestHistory row : rows) {
            content.add(result(row));
        }
        int totalPages = (int) Math.ceil((double) total / size);
        return new RequestHistoryPageResult(List.copyOf(content), page, size, total, totalPages);
    }

    private RequestHistoryEntryResult result(RequestHistory row) {
        HistoryMetadata.Projection metadata = HistoryMetadata.read(row.getMetadata());
        return new RequestHistoryEntryResult(row.getId(), row.getEventType().name(),
                row.getActorId(), name(row.getPreviousStatus()), name(row.getNewStatus()),
                metadata.reason(), metadata.workerId(), metadata.previousWorkerId(),
                metadata.visitId(), metadata.duplicateOfId(), metadata.changedFields(),
                metadata.commentId(), row.getCreatedAt());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
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
