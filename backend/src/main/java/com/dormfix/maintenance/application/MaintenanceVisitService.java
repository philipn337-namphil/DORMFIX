package com.dormfix.maintenance.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.MaintenanceVisit;
import com.dormfix.maintenance.domain.RequestHistory;
import com.dormfix.maintenance.domain.RequestHistoryEventType;
import com.dormfix.maintenance.domain.RequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Visit scheduling, rescheduling and the three outcome commands. The visit worker is always the
 * request's current active assignee, resolved here rather than supplied by a client, so a visit can
 * never name somebody with no authority over the request.
 */
@Service
public class MaintenanceVisitService {
    private final MaintenanceVisitRepository visits;
    private final MaintenanceRequestRepository requests;
    private final MaintenanceAssignmentRepository assignments;
    private final RequestHistoryRepository histories;
    private final AdminDormitoryScopeRepository adminScopes;
    private final DormitoryScopeResolutionService scopes;
    private final Clock clock;

    public MaintenanceVisitService(MaintenanceVisitRepository visits,
            MaintenanceRequestRepository requests, MaintenanceAssignmentRepository assignments,
            RequestHistoryRepository histories, AdminDormitoryScopeRepository adminScopes,
            DormitoryScopeResolutionService scopes, Clock clock) {
        this.visits = visits;
        this.requests = requests;
        this.assignments = assignments;
        this.histories = histories;
        this.adminScopes = adminScopes;
        this.scopes = scopes;
        this.clock = clock;
    }

    @Transactional
    public VisitResult schedule(ScheduleVisitCommand command, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(command.requestId())
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(command.requestId()));
        requireManageAuthority(request, actorId, roles);
        Long workerId = resolveVisitWorker(request);

        if (!request.isIn(RequestStatus.ASSIGNED, RequestStatus.IN_PROGRESS,
                RequestStatus.ON_HOLD)) {
            throw new MaintenanceRequestConflictException(
                    "The request is not in a state that accepts a visit.");
        }
        requireWindow(command.scheduledStart(), command.scheduledEnd());
        if (visits.findOpenByRequestId(request.getId()).isPresent()) {
            throw new MaintenanceRequestConflictException(
                    "The request already has an open visit.");
        }

        Instant now = Instant.now(clock);
        MaintenanceVisit visit = new MaintenanceVisit(request.getId(), workerId, actorId,
                command.scheduledStart(), command.scheduledEnd(), request.getEntryPolicy(),
                request.isContactBeforeEntry(), command.note(), now);
        MaintenanceVisit saved;
        try {
            saved = visits.saveAndFlush(visit);
        } catch (DataIntegrityViolationException error) {
            // ux_visit_open_request lost the race with a concurrent scheduling command.
            throw new MaintenanceRequestConflictException("The request already has an open visit.");
        }
        audit(request, actorId, RequestHistoryEventType.VISIT_SCHEDULED, saved.getId(), now);
        return result(saved);
    }

    @Transactional
    public VisitResult update(UpdateVisitCommand command, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceVisit visit = load(command.visitId());
        MaintenanceRequest request = requestOf(visit);
        requireManageAuthority(request, actorId, roles);
        requireOpen(visit);

        boolean hasStart = command.scheduledStart() != null;
        boolean hasEnd = command.scheduledEnd() != null;
        if (hasStart != hasEnd) {
            throw new InvalidVisitTimeException(
                    "Both scheduled timestamps are required together.");
        }

        Instant now = Instant.now(clock);
        boolean moved = false;
        if (hasStart) {
            requireWindow(command.scheduledStart(), command.scheduledEnd());
            moved = visit.reschedule(command.scheduledStart(), command.scheduledEnd(), now);
        }
        if (command.noteSupplied()) {
            visit.changeNote(command.note(), now);
        }
        MaintenanceVisit saved = visits.saveAndFlush(visit);
        if (moved) {
            // A note-only patch is not a schedule event, so it appends nothing.
            audit(request, actorId, RequestHistoryEventType.VISIT_RESCHEDULED, saved.getId(), now);
        }
        return result(saved);
    }

    @Transactional
    public VisitResult complete(VisitOutcomeCommand command, Long actorId, Set<String> roleNames) {
        return outcome(command, actorId, roleNames, RequestHistoryEventType.VISIT_COMPLETED, true,
                (visit, now) -> {
                    if (command.actualStartedAt() == null || command.actualEndedAt() == null) {
                        throw new InvalidVisitTimeException(
                                "Both actual timestamps are required to complete a visit.");
                    }
                    if (!command.actualStartedAt().isBefore(command.actualEndedAt())) {
                        throw new InvalidVisitTimeException(
                                "Actual start must precede actual end.");
                    }
                    visit.complete(command.actualStartedAt(), command.actualEndedAt(),
                            command.note(), now);
                });
    }

    @Transactional
    public VisitResult recordNoAccess(VisitOutcomeCommand command, Long actorId,
            Set<String> roleNames) {
        requireNote(command);
        return outcome(command, actorId, roleNames, RequestHistoryEventType.VISIT_NO_ACCESS, true,
                (visit, now) -> visit.recordNoAccess(command.note(), now));
    }

    /**
     * Cancel stays available even on a terminal request, because that is what clears a visit left
     * open when its request was closed.
     */
    @Transactional
    public VisitResult cancel(VisitOutcomeCommand command, Long actorId, Set<String> roleNames) {
        requireNote(command);
        return outcome(command, actorId, roleNames, RequestHistoryEventType.VISIT_CANCELED, false,
                (visit, now) -> visit.cancel(command.note(), now));
    }

    @Transactional(readOnly = true)
    public List<VisitResult> listForRequest(Long requestId, Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = requests.findById(requestId)
                .orElseThrow(() -> new MaintenanceRequestNotFoundException(requestId));
        if (!isReadable(request, actorId, roles)) {
            throw new MaintenanceRequestNotFoundException(requestId);
        }
        List<VisitResult> results = new ArrayList<>();
        for (MaintenanceVisit visit : visits.findAllByRequestId(requestId)) {
            results.add(result(visit));
        }
        return List.copyOf(results);
    }

    private VisitResult outcome(VisitOutcomeCommand command, Long actorId, Set<String> roleNames,
            RequestHistoryEventType event, boolean requireLiveRequest, Outcome outcome) {
        Set<Role> roles = roles(roleNames);
        MaintenanceVisit visit = load(command.visitId());
        MaintenanceRequest request = requestOf(visit);
        requireManageAuthority(request, actorId, roles);
        requireOpen(visit);
        if (requireLiveRequest && isTerminal(request)) {
            throw new MaintenanceRequestConflictException(
                    "The request is terminal and cannot take this visit outcome.");
        }
        Instant now = Instant.now(clock);
        outcome.apply(visit, now);
        MaintenanceVisit saved = visits.saveAndFlush(visit);
        audit(request, actorId, event, saved.getId(), now);
        return result(saved);
    }

    private interface Outcome {
        void apply(MaintenanceVisit visit, Instant occurredAt);
    }

    private void requireNote(VisitOutcomeCommand command) {
        if (command.note() == null || command.note().isBlank()) {
            throw new InvalidMaintenanceQueryException("note is required.");
        }
    }

    /**
     * Authorization only, evaluated before any state check. It deliberately does not require an
     * active assignment: a closed request has none, and cancel must still reach an open visit left
     * behind by that close.
     */
    private void requireManageAuthority(MaintenanceRequest request, Long actorId,
            Set<Role> roles) {
        if (roles.contains(Role.ADMIN) || roles.contains(Role.SUPER_ADMIN)) {
            scopes.requireSpaceManageAccess(actorId, roles, request.getSpaceId());
            return;
        }
        if (roles.contains(Role.WORKER)) {
            var active = assignments.findActiveByRequestId(request.getId());
            if (active.isEmpty() || !active.get().getWorkerId().equals(actorId)) {
                throw new WorkerNotAssignedException();
            }
            return;
        }
        throw new DormitoryScopeAccessDeniedException();
    }

    /**
     * The worker a new visit belongs to. Only scheduling needs this, so only scheduling requires
     * the request to have an active assignment.
     */
    private Long resolveVisitWorker(MaintenanceRequest request) {
        return assignments.findActiveByRequestId(request.getId())
                .map(assignment -> assignment.getWorkerId())
                .orElseThrow(() -> new MaintenanceRequestConflictException(
                        "The request has no active assignment."));
    }

    private boolean isReadable(MaintenanceRequest request, Long actorId, Set<Role> roles) {
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
        return roles.contains(Role.ADMIN) && adminScopes.findAllByUserId(actorId).stream()
                .map(scope -> scope.getDormitoryId()).collect(Collectors.toSet())
                .contains(scopes.resolveDormitoryIdForSpace(request.getSpaceId()));
    }

    private boolean isTerminal(MaintenanceRequest request) {
        return request.isIn(RequestStatus.CLOSED, RequestStatus.REJECTED, RequestStatus.DUPLICATE);
    }

    private MaintenanceVisit load(Long visitId) {
        return visits.findById(visitId).orElseThrow(() -> new VisitNotFoundException(visitId));
    }

    private MaintenanceRequest requestOf(MaintenanceVisit visit) {
        return requests.findById(visit.getRequestId()).orElseThrow(
                () -> new MaintenanceRequestNotFoundException(visit.getRequestId()));
    }

    private void requireOpen(MaintenanceVisit visit) {
        if (!visit.isOpen()) {
            throw new MaintenanceRequestConflictException("The visit is no longer open.");
        }
    }

    private void requireWindow(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new InvalidVisitTimeException("Both scheduled timestamps are required.");
        }
        if (!start.isBefore(end)) {
            throw new InvalidVisitTimeException("Scheduled start must precede scheduled end.");
        }
    }

    private void audit(MaintenanceRequest request, Long actorId, RequestHistoryEventType event,
            Long visitId, Instant now) {
        histories.save(new RequestHistory(request.getId(), actorId, event, request.getStatus(),
                request.getStatus(), now, "{\"visitId\":" + visitId + "}"));
    }

    private VisitResult result(MaintenanceVisit visit) {
        return new VisitResult(visit.getId(), visit.getRequestId(), visit.getWorkerId(),
                visit.getCreatedById(), visit.getScheduledStart(), visit.getScheduledEnd(),
                visit.getActualStartedAt(), visit.getActualEndedAt(), visit.getStatus().name(),
                visit.getEntryPolicySnapshot().name(), visit.isContactBeforeEntry(),
                visit.getNote(), visit.getCreatedAt(), visit.getUpdatedAt());
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
