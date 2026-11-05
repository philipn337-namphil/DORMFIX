package com.dormfix.maintenance.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.maintenance.domain.MaintenanceAssignment;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.RequestHistory;
import com.dormfix.maintenance.domain.RequestHistoryEventType;
import com.dormfix.maintenance.domain.RequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The eight lifecycle transitions. Each is one transaction that checks authorization first, then
 * the submitted version, then the source state, and finally performs the transition plus exactly
 * one append-only RequestHistory row. The frozen matrix owns every source and target.
 */
@Service
public class MaintenanceRequestLifecycleService {
    private final MaintenanceRequestRepository requests;
    private final MaintenanceAssignmentRepository assignments;
    private final RequestHistoryRepository histories;
    private final AdminDormitoryScopeRepository adminScopes;
    private final DormitoryScopeResolutionService scopes;
    private final Clock clock;

    public MaintenanceRequestLifecycleService(MaintenanceRequestRepository requests,
            MaintenanceAssignmentRepository assignments, RequestHistoryRepository histories,
            AdminDormitoryScopeRepository adminScopes, DormitoryScopeResolutionService scopes,
            Clock clock) {
        this.requests = requests;
        this.assignments = assignments;
        this.histories = histories;
        this.adminScopes = adminScopes;
        this.scopes = scopes;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceRequestResult start(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        return workerCommand(command, actorId, roleNames, RequestStatus.ASSIGNED,
                RequestHistoryEventType.REQUEST_STARTED, MaintenanceRequest::start);
    }

    @Transactional
    public MaintenanceRequestResult hold(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        requireReason(command);
        return workerCommand(command, actorId, roleNames, RequestStatus.IN_PROGRESS,
                RequestHistoryEventType.REQUEST_HELD, MaintenanceRequest::hold);
    }

    @Transactional
    public MaintenanceRequestResult resume(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        return workerCommand(command, actorId, roleNames, RequestStatus.ON_HOLD,
                RequestHistoryEventType.REQUEST_RESUMED, MaintenanceRequest::resume);
    }

    @Transactional
    public MaintenanceRequestResult resolve(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        return workerCommand(command, actorId, roleNames, RequestStatus.IN_PROGRESS,
                RequestHistoryEventType.REQUEST_RESOLVED, MaintenanceRequest::resolve);
    }

    @Transactional
    public MaintenanceRequestResult close(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        MaintenanceRequest request = load(command, actorId, roleNames, Authority.REPORTER_OR_ADMIN,
                RequestStatus.RESOLVED);
        Instant now = Instant.now(clock);
        RequestStatus previous = request.close(now, command.statusReason());
        endActiveAssignment(request, actorId, now);
        return commit(request, actorId, RequestHistoryEventType.REQUEST_CLOSED, previous, now, null);
    }

    @Transactional
    public MaintenanceRequestResult reopen(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        MaintenanceRequest request = load(command, actorId, roleNames, Authority.REPORTER_OR_ADMIN,
                RequestStatus.RESOLVED);
        if (assignments.findActiveByRequestId(request.getId()).isEmpty()) {
            // IN_PROGRESS with nobody assigned would contradict the matrix rule that only the
            // current active worker may act, so an administrator assigns before reopening.
            throw new MaintenanceRequestConflictException(
                    "The request has no active assignment to reopen into.");
        }
        Instant now = Instant.now(clock);
        RequestStatus previous = request.reopen(now, command.statusReason());
        return commit(request, actorId, RequestHistoryEventType.REQUEST_REOPENED, previous, now,
                null);
    }

    @Transactional
    public MaintenanceRequestResult reject(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        requireReason(command);
        MaintenanceRequest request = load(command, actorId, roleNames, Authority.ADMIN_ONLY,
                RequestStatus.REPORTED);
        Instant now = Instant.now(clock);
        RequestStatus previous = request.reject(now, command.statusReason());
        endActiveAssignment(request, actorId, now);
        return commit(request, actorId, RequestHistoryEventType.REQUEST_REJECTED, previous, now,
                null);
    }

    @Transactional
    public MaintenanceRequestResult markDuplicate(LifecycleCommand command, Long actorId,
            Set<String> roleNames) {
        if (command.duplicateOfId() == null) {
            throw new InvalidMaintenanceQueryException("duplicateOfId is required.");
        }
        MaintenanceRequest request = load(command, actorId, roleNames, Authority.ADMIN_ONLY,
                RequestStatus.REPORTED);
        requireUsableOriginal(command.duplicateOfId(), request, actorId, roles(roleNames));

        Instant now = Instant.now(clock);
        RequestStatus previous = request.markDuplicate(command.duplicateOfId(), now,
                command.statusReason());
        endActiveAssignment(request, actorId, now);
        return commit(request, actorId, RequestHistoryEventType.REQUEST_MARKED_DUPLICATE, previous,
                now, "{\"duplicateOfId\":" + command.duplicateOfId() + "}");
    }

    /**
     * The four worker transitions differ only in their source state, their event type and which
     * named command on the aggregate they call, so they share one body.
     */
    private MaintenanceRequestResult workerCommand(LifecycleCommand command, Long actorId,
            Set<String> roleNames, RequestStatus expected, RequestHistoryEventType event,
            Transition transition) {
        MaintenanceRequest request =
                load(command, actorId, roleNames, Authority.CURRENT_WORKER, expected);
        Instant now = Instant.now(clock);
        RequestStatus previous = transition.apply(request, now, command.statusReason());
        return commit(request, actorId, event, previous, now, null);
    }

    private void requireReason(LifecycleCommand command) {
        if (command.statusReason() == null || command.statusReason().isBlank()) {
            throw new InvalidMaintenanceQueryException("statusReason is required.");
        }
    }

    /** One named lifecycle command on the aggregate, returning the status it moved from. */
    private interface Transition {
        RequestStatus apply(MaintenanceRequest request, Instant occurredAt, String reason);
    }

    private enum Authority {
        CURRENT_WORKER, REPORTER_OR_ADMIN, ADMIN_ONLY
    }

    /**
     * Authorization runs before the version and state checks so a conflict response never reveals
     * the state of a request the caller may not act on.
     */
    private MaintenanceRequest load(LifecycleCommand command, Long actorId, Set<String> roleNames,
            Authority authority, RequestStatus expected) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(command.requestId())
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(command.requestId()));
        authorize(authority, request, actorId, roles);
        if (!request.matchesVersion(command.version())) {
            throw new RequestVersionConflictException();
        }
        if (!request.isIn(expected)) {
            throw new MaintenanceRequestConflictException(
                    "The request is not in a state this command accepts.");
        }
        return request;
    }

    private void authorize(Authority authority, MaintenanceRequest request, Long actorId,
            Set<Role> roles) {
        switch (authority) {
            case CURRENT_WORKER -> requireCurrentWorker(request, actorId, roles);
            case REPORTER_OR_ADMIN -> requireReporterOrScopedAdmin(request, actorId, roles);
            case ADMIN_ONLY -> scopes.requireSpaceManageAccess(actorId, roles, request.getSpaceId());
        }
    }

    private void requireCurrentWorker(MaintenanceRequest request, Long actorId, Set<Role> roles) {
        if (!roles.contains(Role.WORKER)) {
            throw new DormitoryScopeAccessDeniedException();
        }
        Optional<MaintenanceAssignment> active = assignments.findActiveByRequestId(request.getId());
        if (active.isEmpty() || !active.get().getWorkerId().equals(actorId)) {
            throw new WorkerNotAssignedException();
        }
    }

    private void requireReporterOrScopedAdmin(MaintenanceRequest request, Long actorId,
            Set<Role> roles) {
        if (roles.contains(Role.RESIDENT) && request.getReporterId().equals(actorId)) {
            return;
        }
        if (roles.contains(Role.ADMIN) || roles.contains(Role.SUPER_ADMIN)) {
            scopes.requireSpaceManageAccess(actorId, roles, request.getSpaceId());
            return;
        }
        throw new DormitoryScopeAccessDeniedException();
    }

    /**
     * The original must exist and be visible to the actor, otherwise the answer is the same 404 as
     * a missing request. V1 allows no chain, so the original must not itself be a duplicate.
     */
    private void requireUsableOriginal(Long originalId, MaintenanceRequest request, Long actorId,
            Set<Role> roles) {
        if (originalId.equals(request.getId())) {
            throw new MaintenanceRequestConflictException("A request cannot duplicate itself.");
        }
        MaintenanceRequest original = requests.findById(originalId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(originalId));
        if (!isVisible(original, actorId, roles)) {
            throw new MaintenanceRequestNotFoundException(originalId);
        }
        if (original.isIn(RequestStatus.DUPLICATE)) {
            throw new MaintenanceRequestConflictException(
                    "The referenced original is itself a duplicate.");
        }
    }

    private boolean isVisible(MaintenanceRequest request, Long actorId, Set<Role> roles) {
        if (roles.contains(Role.SUPER_ADMIN)) {
            return true;
        }
        return roles.contains(Role.ADMIN) && adminScopes.findAllByUserId(actorId).stream()
                .map(scope -> scope.getDormitoryId()).collect(Collectors.toSet())
                .contains(scopes.resolveDormitoryIdForSpace(request.getSpaceId()));
    }

    private void endActiveAssignment(MaintenanceRequest request, Long actorId, Instant now) {
        assignments.findActiveByRequestId(request.getId()).ifPresent(assignment -> {
            assignment.end(actorId, now);
            assignments.saveAndFlush(assignment);
        });
    }

    private MaintenanceRequestResult commit(MaintenanceRequest request, Long actorId,
            RequestHistoryEventType event, RequestStatus previous, Instant now, String metadata) {
        MaintenanceRequest saved;
        try {
            saved = requests.saveAndFlush(request);
        } catch (ObjectOptimisticLockingFailureException error) {
            throw new RequestVersionConflictException();
        }
        histories.save(new RequestHistory(saved.getId(), actorId, event, previous,
                saved.getStatus(), now, metadata));
        return new MaintenanceRequestResult(saved.getId(), saved.getRequestNumber(),
                saved.getStatus().name(), saved.getPriority().name(), saved.getVersion());
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
