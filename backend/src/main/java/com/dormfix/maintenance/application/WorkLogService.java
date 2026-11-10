package com.dormfix.maintenance.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.RequestStatus;
import com.dormfix.maintenance.domain.WorkActionType;
import com.dormfix.maintenance.domain.WorkLog;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only work logs. Appending one does not touch the request: no status change, no version
 * check, and no RequestHistory row, because this table is itself the durable record of the work.
 */
@Service
public class WorkLogService {
    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 100;

    private final WorkLogRepository workLogs;
    private final MaintenanceRequestRepository requests;
    private final MaintenanceVisitRepository visits;
    private final MaintenanceAssignmentRepository assignments;
    private final AdminDormitoryScopeRepository adminScopes;
    private final DormitoryScopeResolutionService scopes;
    private final Clock clock;

    public WorkLogService(WorkLogRepository workLogs, MaintenanceRequestRepository requests,
            MaintenanceVisitRepository visits, MaintenanceAssignmentRepository assignments,
            AdminDormitoryScopeRepository adminScopes, DormitoryScopeResolutionService scopes,
            Clock clock) {
        this.workLogs = workLogs;
        this.requests = requests;
        this.visits = visits;
        this.assignments = assignments;
        this.adminScopes = adminScopes;
        this.scopes = scopes;
        this.clock = clock;
    }

    @Transactional
    public WorkLogResult append(AppendWorkLogCommand command, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(command.requestId())
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(command.requestId()));

        // Authorization before any state or reference check.
        requireCurrentAssignee(request, actorId, roles);

        if (request.isIn(RequestStatus.CLOSED, RequestStatus.REJECTED, RequestStatus.DUPLICATE)) {
            // Unreachable in practice: a terminal transition ends the assignment, so the authority
            // check above already stops a worker. Kept so the invariant does not rely on that.
            throw new MaintenanceRequestConflictException(
                    "The request is terminal and cannot take a work log.");
        }
        WorkActionType actionType = actionType(command.actionType());
        requireVisitOfSameRequest(command.visitId(), request.getId());

        WorkLog saved = workLogs.save(new WorkLog(request.getId(), command.visitId(), actorId,
                actionType, command.description(), Instant.now(clock)));
        return result(saved);
    }

    @Transactional(readOnly = true)
    public WorkLogPageResult listForRequest(Long requestId, int page, int size, Long actorId,
            Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        validatePagination(page, size);
        MaintenanceRequest request = requests.findById(requestId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(requestId));
        requireStaffReader(request, actorId, roles);

        List<WorkLogResult> content = new ArrayList<>();
        for (WorkLog workLog : workLogs.findPageByRequestId(requestId, page, size)) {
            content.add(result(workLog));
        }
        long total = workLogs.countByRequestId(requestId);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new WorkLogPageResult(List.copyOf(content), page, size, total, totalPages);
    }

    private void requireCurrentAssignee(MaintenanceRequest request, Long actorId,
            Set<Role> roles) {
        if (!roles.contains(Role.WORKER)) {
            // Administrators do not append work logs: worker_id means the person who did the work.
            throw new DormitoryScopeAccessDeniedException();
        }
        var active = assignments.findActiveByRequestId(request.getId());
        if (active.isEmpty() || !active.get().getWorkerId().equals(actorId)) {
            throw new WorkerNotAssignedException();
        }
    }

    /**
     * Work logs are the internal record of the work, so a RESIDENT is denied rather than hidden
     * from: they already know their own request exists, so 403 discloses nothing new. A request
     * they cannot see at all still answers 404 through the lookup above.
     */
    private void requireStaffReader(MaintenanceRequest request, Long actorId, Set<Role> roles) {
        if (roles.contains(Role.SUPER_ADMIN)) {
            return;
        }
        if (roles.contains(Role.WORKER)
                && assignments.existsByRequestIdAndWorkerId(request.getId(), actorId)) {
            return;
        }
        if (roles.contains(Role.ADMIN) && adminScopes.findAllByUserId(actorId).stream()
                .map(scope -> scope.getDormitoryId()).collect(Collectors.toSet())
                .contains(scopes.resolveDormitoryIdForSpace(request.getSpaceId()))) {
            return;
        }
        throw new DormitoryScopeAccessDeniedException();
    }

    /**
     * The visit must belong to this request. The constraint spans two rows, so the foreign key
     * cannot express it and the application enforces it.
     */
    private void requireVisitOfSameRequest(Long visitId, Long requestId) {
        if (visitId == null) {
            return;
        }
        var visit = visits.findById(visitId)
                .orElseThrow(() -> new VisitNotFoundException(visitId));
        if (!visit.getRequestId().equals(requestId)) {
            throw new MaintenanceRequestConflictException(
                    "The visit does not belong to this request.");
        }
    }

    private WorkActionType actionType(String value) {
        try {
            return WorkActionType.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new InvalidMaintenanceQueryException("Invalid work action type.");
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || size < MIN_SIZE || size > MAX_SIZE) {
            throw new InvalidMaintenanceQueryException("Invalid pagination.");
        }
    }

    private WorkLogResult result(WorkLog workLog) {
        return new WorkLogResult(workLog.getId(), workLog.getRequestId(), workLog.getVisitId(),
                workLog.getWorkerId(), workLog.getActionType().name(), workLog.getDescription(),
                workLog.getCreatedAt());
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
