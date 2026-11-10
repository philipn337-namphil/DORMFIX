package com.dormfix.maintenance.application;

import com.dormfix.catalog.application.FacilityRepository;
import com.dormfix.catalog.application.MaintenanceCategoryNotFoundException;
import com.dormfix.catalog.application.MaintenanceCategoryRepository;
import com.dormfix.catalog.domain.FacilityStatus;
import com.dormfix.catalog.domain.Priority;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.location.application.FacilityNotFoundException;
import com.dormfix.maintenance.domain.EntryPolicy;
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
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metadata PATCH. The resident and administrator field sets do not intersect and arrive as two
 * different commands, so a resident can never reach priority and an administrator can never reach
 * entry consent through a missed runtime check. Neither command carries the space: the reporting
 * rule anchors it to the reporter's residence, and moving it would hollow that rule out.
 */
@Service
public class MaintenanceRequestPatchService {
    private final MaintenanceRequestRepository requests;
    private final MaintenanceAssignmentRepository assignments;
    private final RequestHistoryRepository histories;
    private final MaintenanceCategoryRepository categories;
    private final FacilityRepository facilities;
    private final DormitoryScopeResolutionService scopes;
    private final Clock clock;

    public MaintenanceRequestPatchService(MaintenanceRequestRepository requests,
            MaintenanceAssignmentRepository assignments, RequestHistoryRepository histories,
            MaintenanceCategoryRepository categories, FacilityRepository facilities,
            DormitoryScopeResolutionService scopes, Clock clock) {
        this.requests = requests;
        this.assignments = assignments;
        this.histories = histories;
        this.categories = categories;
        this.facilities = facilities;
        this.scopes = scopes;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceRequestDetailResult patchAsResident(ResidentPatchCommand command,
            Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = load(command.requestId());

        // Authorization before the version and state checks.
        if (!roles.contains(Role.RESIDENT) || !request.getReporterId().equals(actorId)) {
            throw new DormitoryScopeAccessDeniedException();
        }
        requireVersion(request, command.version());
        if (!request.isIn(RequestStatus.REPORTED)) {
            throw new MaintenanceRequestConflictException(
                    "The request can no longer be edited by its reporter.");
        }

        EntryPolicy entryPolicy = command.entryPolicy().present()
                ? entryPolicy(command.entryPolicy().value()) : request.getEntryPolicy();
        Instant start = command.preferredVisitStart().orElse(request.getPreferredVisitStart());
        Instant end = command.preferredVisitEnd().orElse(request.getPreferredVisitEnd());
        requirePreferredWindow(start, end);

        Instant now = Instant.now(clock);
        List<String> changed = request.reviseReport(
                command.title().orElse(request.getTitle()),
                command.description().orElse(request.getDescription()), entryPolicy,
                command.contactBeforeEntry().orElse(request.isContactBeforeEntry()), start, end,
                now);
        if (changed.isEmpty()) {
            return detail(request);
        }
        MaintenanceRequest saved = save(request);
        audit(saved, actorId, RequestHistoryEventType.REQUEST_METADATA_UPDATED, changed, now);
        return detail(saved);
    }

    @Transactional
    public MaintenanceRequestDetailResult patchAsAdministrator(AdminPatchCommand command,
            Long actorId, Set<String> roleNames) {
        Set<Role> roles = roles(roleNames);
        MaintenanceRequest request = load(command.requestId());

        if (!roles.contains(Role.ADMIN) && !roles.contains(Role.SUPER_ADMIN)) {
            throw new DormitoryScopeAccessDeniedException();
        }
        scopes.requireSpaceManageAccess(actorId, roles, request.getSpaceId());
        requireVersion(request, command.version());
        if (!request.isActive()) {
            throw new MaintenanceRequestConflictException(
                    "The request is not in a state that accepts reclassification.");
        }

        Long categoryId = command.categoryId().orElse(request.getCategoryId());
        if (!categoryId.equals(request.getCategoryId())) {
            requireActiveCategory(categoryId);
        }
        Priority priority = command.priority().present() ? priority(command.priority().value())
                : request.getPriority();
        Long facilityId = command.facilityId().orElse(request.getFacilityId());
        if (facilityId != null && !facilityId.equals(request.getFacilityId())) {
            requireUsableFacility(facilityId, request.getSpaceId());
        }

        Instant now = Instant.now(clock);
        List<String> changed = request.reclassify(categoryId, priority, facilityId, now);
        if (changed.isEmpty()) {
            return detail(request);
        }
        MaintenanceRequest saved = save(request);
        // CATEGORY_CHANGED and PRIORITY_CHANGED are separate frozen event names, so a patch that
        // changes both appends both rows rather than folding them into one.
        if (changed.contains("categoryId")) {
            audit(saved, actorId, RequestHistoryEventType.CATEGORY_CHANGED, List.of("categoryId"),
                    now);
        }
        if (changed.contains("priority")) {
            audit(saved, actorId, RequestHistoryEventType.PRIORITY_CHANGED, List.of("priority"),
                    now);
        }
        List<String> others = new ArrayList<>(changed);
        others.remove("categoryId");
        others.remove("priority");
        if (!others.isEmpty()) {
            audit(saved, actorId, RequestHistoryEventType.REQUEST_METADATA_UPDATED, others, now);
        }
        return detail(saved);
    }

    private MaintenanceRequest load(Long id) {
        return requests.findById(id).orElseThrow(() -> new MaintenanceRequestNotFoundException(id));
    }

    private void requireVersion(MaintenanceRequest request, Long version) {
        if (!request.matchesVersion(version)) {
            throw new RequestVersionConflictException();
        }
    }

    private void requirePreferredWindow(Instant start, Instant end) {
        boolean hasStart = start != null;
        boolean hasEnd = end != null;
        if (hasStart != hasEnd || hasStart && !start.isBefore(end)) {
            throw new InvalidPreferredVisitTimeException();
        }
    }

    private void requireActiveCategory(Long categoryId) {
        var category = categories.findById(categoryId)
                .orElseThrow(() -> new MaintenanceCategoryNotFoundException(categoryId));
        if (!category.isActive()) {
            throw new MaintenanceRequestConflictException("Maintenance category is inactive.");
        }
    }

    /** Same rule as creation: the facility belongs to the request space and is not RETIRED. */
    private void requireUsableFacility(Long facilityId, Long spaceId) {
        var facility = facilities.findById(facilityId)
                .orElseThrow(() -> new FacilityNotFoundException(facilityId));
        if (!facility.getSpaceId().equals(spaceId)
                || facility.getStatus() == FacilityStatus.RETIRED) {
            throw new MaintenanceRequestConflictException(
                    "Facility is not valid for the request space.");
        }
    }

    private EntryPolicy entryPolicy(String value) {
        try {
            return EntryPolicy.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new InvalidEntryPolicyException();
        }
    }

    private Priority priority(String value) {
        try {
            return Priority.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new InvalidMaintenanceQueryException("Invalid priority.");
        }
    }

    private MaintenanceRequest save(MaintenanceRequest request) {
        try {
            return requests.saveAndFlush(request);
        } catch (ObjectOptimisticLockingFailureException error) {
            throw new RequestVersionConflictException();
        }
    }

    /** Field names only. Values would copy entry consent and free text into audit rows. */
    private void audit(MaintenanceRequest request, Long actorId, RequestHistoryEventType event,
            List<String> fields, Instant now) {
        String names = fields.stream().map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(","));
        histories.save(new RequestHistory(request.getId(), actorId, event, request.getStatus(),
                request.getStatus(), now, "{\"fields\":[" + names + "]}"));
    }

    private MaintenanceRequestDetailResult detail(MaintenanceRequest request) {
        Long assigneeId = assignments.findActiveByRequestId(request.getId())
                .map(assignment -> assignment.getWorkerId()).orElse(null);
        return MaintenanceRequestDetailResult.of(request, assigneeId);
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
