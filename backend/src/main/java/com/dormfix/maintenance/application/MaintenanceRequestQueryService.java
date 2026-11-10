package com.dormfix.maintenance.application;

import com.dormfix.catalog.domain.Priority;
import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.RequestStatus;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the MaintenanceRequest contract. Visibility is anchored per role: a RESIDENT sees
 * what they reported, a WORKER sees what they are or were assigned to, an ADMIN sees their
 * dormitory scopes, and a SUPER_ADMIN sees everything. A detail read outside visibility answers
 * the same 404 as a missing request, so the path never discloses existence.
 */
@Service
public class MaintenanceRequestQueryService {
    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 100;

    private final MaintenanceRequestRepository requests;
    private final MaintenanceAssignmentRepository assignments;
    private final AdminDormitoryScopeRepository adminScopes;
    private final DormitoryScopeResolutionService scopes;

    public MaintenanceRequestQueryService(MaintenanceRequestRepository requests,
            MaintenanceAssignmentRepository assignments, AdminDormitoryScopeRepository adminScopes,
            DormitoryScopeResolutionService scopes) {
        this.requests = requests;
        this.assignments = assignments;
        this.adminScopes = adminScopes;
        this.scopes = scopes;
    }

    @Transactional(readOnly = true)
    public MaintenanceRequestDetailResult findDetail(Long id, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(id)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(id));
        if (!isVisible(request, actorId, roles)) {
            throw new MaintenanceRequestNotFoundException(id);
        }
        Long assigneeId = assignments.findActiveByRequestId(request.getId())
                .map(assignment -> assignment.getWorkerId()).orElse(null);
        return detail(request, assigneeId);
    }

    @Transactional(readOnly = true)
    public MaintenanceRequestPageResult list(MaintenanceRequestQuery query, Long actorId,
            Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        validatePagination(query.page(), query.size());
        validateStatuses(query.statuses());
        validatePriority(query.priority());
        RequestVisibility visibility = visibility(actorId, roles);
        requireAdminScopeForLocationFilters(query, actorId, roles, visibility);
        if (visibility.deniesEverything()) {
            throw new DormitoryScopeAccessDeniedException();
        }
        return page(requests.findVisiblePage(query, visibility), query.page(), query.size());
    }

    @Transactional(readOnly = true)
    public MaintenanceRequestPageResult listAssignedToMe(Long actorId, Set<String> roleNames,
            boolean active, int page, int size) {
        if (!roles(roleNames).contains(Role.WORKER)) {
            throw new DormitoryScopeAccessDeniedException();
        }
        validatePagination(page, size);
        return page(requests.findAssignedPage(actorId, active, page, size), page, size);
    }

    private boolean isVisible(MaintenanceRequest request, Long actorId, Set<Role> roles) {
        if (roles.contains(Role.SUPER_ADMIN)) {
            return true;
        }
        if (roles.contains(Role.RESIDENT) && request.getReporterId().equals(actorId)) {
            return true;
        }
        if (roles.contains(Role.WORKER)
                && assignments.existsByRequestIdAndWorkerId(request.getId(), actorId)) {
            return true;
        }
        return roles.contains(Role.ADMIN) && adminDormitoryIds(actorId)
                .contains(scopes.resolveDormitoryIdForSpace(request.getSpaceId()));
    }

    private RequestVisibility visibility(Long actorId, Set<Role> roles) {
        return new RequestVisibility(actorId, roles.contains(Role.SUPER_ADMIN),
                roles.contains(Role.RESIDENT), roles.contains(Role.WORKER),
                roles.contains(Role.ADMIN) ? adminDormitoryIds(actorId) : Set.of());
    }

    /**
     * An administrator naming a location outside their own scope is a scope failure rather than an
     * empty page. A caller who is not an administrator is unaffected: their own visibility rule
     * already bounds the result.
     */
    private void requireAdminScopeForLocationFilters(MaintenanceRequestQuery query, Long actorId,
            Set<Role> roles, RequestVisibility visibility) {
        if (visibility.allScopes() || !roles.contains(Role.ADMIN)) {
            return;
        }
        Set<Long> allowed = visibility.dormitoryIds();
        if (query.dormitoryId() != null && !allowed.contains(query.dormitoryId())) {
            throw new DormitoryScopeAccessDeniedException();
        }
        if (query.spaceId() != null
                && !allowed.contains(scopes.resolveDormitoryIdForSpace(query.spaceId()))) {
            throw new DormitoryScopeAccessDeniedException();
        }
        if (query.buildingId() != null
                && !allowed.contains(scopes.resolveDormitoryIdForBuilding(query.buildingId()))) {
            throw new DormitoryScopeAccessDeniedException();
        }
    }

    private Set<Long> adminDormitoryIds(Long actorId) {
        return adminScopes.findAllByUserId(actorId).stream()
                .map(scope -> scope.getDormitoryId()).collect(Collectors.toSet());
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < MIN_SIZE || size > MAX_SIZE) {
            throw new InvalidMaintenanceQueryException("Invalid pagination.");
        }
    }

    private void validateStatuses(Set<String> statuses) {
        if (statuses == null) {
            return;
        }
        for (String status : statuses) {
            try {
                RequestStatus.valueOf(status);
            } catch (IllegalArgumentException | NullPointerException error) {
                throw new InvalidMaintenanceQueryException("Invalid status filter.");
            }
        }
    }

    private void validatePriority(String priority) {
        if (priority == null) {
            return;
        }
        try {
            Priority.valueOf(priority);
        } catch (IllegalArgumentException error) {
            throw new InvalidMaintenanceQueryException("Invalid priority filter.");
        }
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

    private MaintenanceRequestPageResult page(RequestPage source, int page, int size) {
        List<Long> ids = source.content().stream().map(MaintenanceRequest::getId).toList();
        Map<Long, Long> assignees = assignments.findActiveWorkerIds(ids);
        List<MaintenanceRequestSummaryResult> content = new ArrayList<>(source.content().size());
        for (MaintenanceRequest request : source.content()) {
            content.add(summary(request, assignees.get(request.getId())));
        }
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) source.totalElements() / size);
        return new MaintenanceRequestPageResult(List.copyOf(content), page, size,
                source.totalElements(), totalPages);
    }

    private MaintenanceRequestSummaryResult summary(MaintenanceRequest request, Long assigneeId) {
        return new MaintenanceRequestSummaryResult(request.getId(), request.getRequestNumber(),
                request.getStatus().name(), request.getPriority().name(), request.getCategoryId(),
                request.getSpaceId(), request.getFacilityId(), request.getTitle(), assigneeId,
                request.getCreatedAt(), request.getUpdatedAt(), request.getVersion());
    }

    private MaintenanceRequestDetailResult detail(MaintenanceRequest request, Long assigneeId) {
        return MaintenanceRequestDetailResult.of(request, assigneeId);
    }
}
