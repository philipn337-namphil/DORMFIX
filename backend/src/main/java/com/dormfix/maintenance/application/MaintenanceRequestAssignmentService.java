package com.dormfix.maintenance.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Initial assignment and reassignment. One path serves both and the current status selects which.
 * The whole command is one transaction: end the active assignment when reassigning, insert the new
 * one, move the status to ASSIGNED, and append exactly one RequestHistory row.
 */
@Service
public class MaintenanceRequestAssignmentService {
    private final MaintenanceRequestRepository requests;
    private final MaintenanceAssignmentRepository assignments;
    private final RequestHistoryRepository histories;
    private final UserRepository users;
    private final DormitoryScopeResolutionService scopes;
    private final Clock clock;

    public MaintenanceRequestAssignmentService(MaintenanceRequestRepository requests,
            MaintenanceAssignmentRepository assignments, RequestHistoryRepository histories,
            UserRepository users, DormitoryScopeResolutionService scopes, Clock clock) {
        this.requests = requests;
        this.assignments = assignments;
        this.histories = histories;
        this.users = users;
        this.scopes = scopes;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceRequestResult assign(AssignWorkerCommand command, Long actorId,
            Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(command.requestId())
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(command.requestId()));

        // Authorization before any state or reference check, so a conflict message cannot
        // disclose the state of a request the caller is not allowed to act on.
        scopes.requireSpaceManageAccess(actorId, roles, request.getSpaceId());

        if (!request.matchesVersion(command.version())) {
            throw new RequestVersionConflictException();
        }
        if (!request.isAssignable()) {
            throw new MaintenanceRequestConflictException(
                    "Request cannot be assigned in its current state.");
        }
        requireAssignableWorker(command.workerId());

        Optional<MaintenanceAssignment> active =
                assignments.findActiveByRequestId(request.getId());
        if (active.isPresent() && active.get().getWorkerId().equals(command.workerId())) {
            throw new RequestAlreadyAssignedException();
        }

        Instant now = Instant.now(clock);
        boolean reassignment = active.isPresent();
        Long previousWorkerId = active.map(MaintenanceAssignment::getWorkerId).orElse(null);

        if (active.isPresent()) {
            // The ended row must reach the database before the replacement is inserted. Hibernate
            // orders insertions before updates inside one flush, so leaving both pending would
            // insert a second row while the old one still has a null unassigned_at, and
            // ux_assignment_active_request would reject the legitimate reassignment.
            MaintenanceAssignment previous = active.get();
            previous.end(actorId, now);
            assignments.saveAndFlush(previous);
        }

        try {
            assignments.saveAndFlush(new MaintenanceAssignment(request.getId(), command.workerId(),
                    actorId, command.reason(), now));
        } catch (DataIntegrityViolationException error) {
            // ux_assignment_active_request lost the race. This is a competing assignment, not a
            // stale caller, so it must not be reported as VERSION_CONFLICT.
            throw new RequestAlreadyAssignedException();
        }

        RequestStatus previousStatus = request.markAssigned(now);
        try {
            request = requests.saveAndFlush(request);
        } catch (ObjectOptimisticLockingFailureException error) {
            throw new RequestVersionConflictException();
        }

        histories.save(new RequestHistory(request.getId(), actorId,
                reassignment ? RequestHistoryEventType.REQUEST_REASSIGNED
                        : RequestHistoryEventType.REQUEST_ASSIGNED,
                previousStatus, RequestStatus.ASSIGNED, now,
                metadata(command.workerId(), previousWorkerId)));

        return new MaintenanceRequestResult(request.getId(), request.getRequestNumber(),
                request.getStatus().name(), request.getPriority().name(), request.getVersion());
    }

    private void requireAssignableWorker(Long workerId) {
        User worker = users.findById(workerId)
                .orElseThrow(() -> new WorkerNotFoundException(workerId));
        if (!worker.getRoles().contains(Role.WORKER) || worker.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidWorkerException();
        }
    }

    private String metadata(Long workerId, Long previousWorkerId) {
        if (previousWorkerId == null) {
            return "{\"workerId\":" + workerId + "}";
        }
        return "{\"workerId\":" + workerId + ",\"previousWorkerId\":" + previousWorkerId + "}";
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
        return roles;
    }
}
