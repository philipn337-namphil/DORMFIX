package com.dormfix.maintenance.api;

import com.dormfix.maintenance.application.AssignWorkerCommand;
import com.dormfix.maintenance.application.CreateMaintenanceRequestCommand;
import com.dormfix.maintenance.application.LifecycleCommand;
import com.dormfix.maintenance.application.MaintenanceRequestAssignmentService;
import com.dormfix.maintenance.application.MaintenanceRequestCreationService;
import com.dormfix.maintenance.application.MaintenanceRequestLifecycleService;
import com.dormfix.maintenance.application.MaintenanceRequestQuery;
import com.dormfix.maintenance.application.MaintenanceRequestQueryService;
import com.dormfix.maintenance.application.MaintenanceVisitService;
import com.dormfix.maintenance.application.ScheduleVisitCommand;
import com.dormfix.maintenance.application.UpdateVisitCommand;
import com.dormfix.maintenance.application.VisitOutcomeCommand;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaintenanceRequestController {
    private static final String BASE = "/api/v1/maintenance-requests";
    private static final String VISITS = "/api/v1/visits";

    private final MaintenanceRequestCreationService creation;
    private final MaintenanceRequestQueryService queries;
    private final MaintenanceRequestAssignmentService assignments;
    private final MaintenanceRequestLifecycleService lifecycle;
    private final MaintenanceVisitService visits;

    public MaintenanceRequestController(MaintenanceRequestCreationService creation,
            MaintenanceRequestQueryService queries,
            MaintenanceRequestAssignmentService assignments,
            MaintenanceRequestLifecycleService lifecycle, MaintenanceVisitService visits) {
        this.creation = creation;
        this.queries = queries;
        this.assignments = assignments;
        this.lifecycle = lifecycle;
        this.visits = visits;
    }

    @PostMapping(BASE)
    public ResponseEntity<MaintenanceRequestResponse> create(
            @Valid @RequestBody CreateMaintenanceRequest value, @AuthenticationPrincipal Jwt jwt) {
        var result = creation.create(new CreateMaintenanceRequestCommand(value.spaceId(),
                value.facilityId(), value.categoryId(), value.title(), value.description(),
                value.entryPolicy(), value.contactBeforeEntry(), value.preferredVisitStart(),
                value.preferredVisitEnd()), id(jwt), roles(jwt));
        return ResponseEntity.created(URI.create(BASE + "/" + result.id()))
                .body(MaintenanceRequestResponse.from(result));
    }

    @GetMapping(BASE + "/assigned-to-me")
    public MaintenanceRequestPageResponse assignedToMe(
            @RequestParam(defaultValue = "true") boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestPageResponse
                .from(queries.listAssignedToMe(id(jwt), roles(jwt), active, page, size));
    }

    @GetMapping(BASE + "/{id}")
    public MaintenanceRequestDetailResponse detail(@PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestDetailResponse
                .from(queries.findDetail(id, id(jwt), roles(jwt)));
    }

    @GetMapping(BASE)
    public MaintenanceRequestPageResponse list(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long dormitoryId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        var query = new MaintenanceRequestQuery(statuses(status), priority, categoryId, dormitoryId,
                buildingId, spaceId, assigneeId, page, size);
        return MaintenanceRequestPageResponse.from(queries.list(query, id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/assignments")
    public MaintenanceRequestResponse assign(@PathVariable Long id,
            @Valid @RequestBody AssignWorkerRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse.from(assignments.assign(
                new AssignWorkerCommand(id, value.workerId(), value.reason(), value.version()),
                id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/start")
    public MaintenanceRequestResponse start(@PathVariable Long id,
            @Valid @RequestBody LifecycleCommandRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse
                .from(lifecycle.start(command(id, value), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/hold")
    public MaintenanceRequestResponse hold(@PathVariable Long id,
            @Valid @RequestBody LifecycleCommandRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse
                .from(lifecycle.hold(command(id, value), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/resume")
    public MaintenanceRequestResponse resume(@PathVariable Long id,
            @Valid @RequestBody LifecycleCommandRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse
                .from(lifecycle.resume(command(id, value), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/resolve")
    public MaintenanceRequestResponse resolve(@PathVariable Long id,
            @Valid @RequestBody LifecycleCommandRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse
                .from(lifecycle.resolve(command(id, value), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/close")
    public MaintenanceRequestResponse close(@PathVariable Long id,
            @Valid @RequestBody LifecycleCommandRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse
                .from(lifecycle.close(command(id, value), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/reopen")
    public MaintenanceRequestResponse reopen(@PathVariable Long id,
            @Valid @RequestBody LifecycleCommandRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse
                .from(lifecycle.reopen(command(id, value), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/reject")
    public MaintenanceRequestResponse reject(@PathVariable Long id,
            @Valid @RequestBody LifecycleCommandRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse
                .from(lifecycle.reject(command(id, value), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/mark-duplicate")
    public MaintenanceRequestResponse markDuplicate(@PathVariable Long id,
            @Valid @RequestBody MarkDuplicateRequest value, @AuthenticationPrincipal Jwt jwt) {
        return MaintenanceRequestResponse.from(lifecycle.markDuplicate(new LifecycleCommand(id,
                value.statusReason(), value.duplicateOfId(), value.version()), id(jwt), roles(jwt)));
    }

    @PostMapping(BASE + "/{id}/visits")
    public VisitResponse scheduleVisit(@PathVariable Long id,
            @Valid @RequestBody ScheduleVisitRequest value, @AuthenticationPrincipal Jwt jwt) {
        return VisitResponse.forStaff(visits.schedule(new ScheduleVisitCommand(id,
                value.scheduledStart(), value.scheduledEnd(), value.note()), id(jwt), roles(jwt)));
    }

    @GetMapping(BASE + "/{id}/visits")
    public VisitListResponse listVisits(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        Set<String> roles = roles(jwt);
        return VisitListResponse.from(visits.listForRequest(id, id(jwt), roles), roles);
    }

    @PatchMapping(VISITS + "/{visitId}")
    public VisitResponse updateVisit(@PathVariable Long visitId,
            @Valid @RequestBody UpdateVisitRequest value, @AuthenticationPrincipal Jwt jwt) {
        // A supplied note replaces the stored one; omitting it leaves the note alone. V1 has no
        // path that clears a note, which the contract does not ask for.
        return VisitResponse.forStaff(visits.update(new UpdateVisitCommand(visitId,
                value.scheduledStart(), value.scheduledEnd(), value.note(), value.note() != null),
                id(jwt), roles(jwt)));
    }

    @PostMapping(VISITS + "/{visitId}/complete")
    public VisitResponse completeVisit(@PathVariable Long visitId,
            @Valid @RequestBody CompleteVisitRequest value, @AuthenticationPrincipal Jwt jwt) {
        return VisitResponse.forStaff(visits.complete(new VisitOutcomeCommand(visitId,
                value.actualStartedAt(), value.actualEndedAt(), value.note()), id(jwt),
                roles(jwt)));
    }

    @PostMapping(VISITS + "/{visitId}/no-access")
    public VisitResponse visitNoAccess(@PathVariable Long visitId,
            @Valid @RequestBody VisitOutcomeRequest value, @AuthenticationPrincipal Jwt jwt) {
        return VisitResponse.forStaff(visits.recordNoAccess(
                new VisitOutcomeCommand(visitId, null, null, value.note()), id(jwt), roles(jwt)));
    }

    @PostMapping(VISITS + "/{visitId}/cancel")
    public VisitResponse cancelVisit(@PathVariable Long visitId,
            @Valid @RequestBody VisitOutcomeRequest value, @AuthenticationPrincipal Jwt jwt) {
        return VisitResponse.forStaff(visits.cancel(
                new VisitOutcomeCommand(visitId, null, null, value.note()), id(jwt), roles(jwt)));
    }

    private LifecycleCommand command(Long id, LifecycleCommandRequest value) {
        return new LifecycleCommand(id, value.statusReason(), null, value.version());
    }

    private Set<String> statuses(List<String> values) {
        return values == null ? Set.of() : new LinkedHashSet<>(values);
    }

    private Long id(Jwt jwt) {
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException error) {
            throw new AccessDeniedException("Access is denied.");
        }
    }

    private Set<String> roles(Jwt jwt) {
        List<String> values = jwt.getClaimAsStringList("roles");
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
