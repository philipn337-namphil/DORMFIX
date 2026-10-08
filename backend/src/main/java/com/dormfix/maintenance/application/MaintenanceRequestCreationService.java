package com.dormfix.maintenance.application;

import com.dormfix.catalog.application.FacilityRepository;
import com.dormfix.catalog.application.MaintenanceCategoryNotFoundException;
import com.dormfix.catalog.application.MaintenanceCategoryRepository;
import com.dormfix.catalog.domain.FacilityStatus;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.application.UserNotFoundException;
import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.BuildingNotFoundException;
import com.dormfix.location.application.BuildingRepository;
import com.dormfix.location.application.DormitoryNotFoundException;
import com.dormfix.location.application.DormitoryRepository;
import com.dormfix.location.application.FacilityNotFoundException;
import com.dormfix.location.application.ResidenceRepository;
import com.dormfix.location.application.SpaceNotFoundException;
import com.dormfix.location.application.SpaceRepository;
import com.dormfix.location.domain.SpaceType;
import com.dormfix.maintenance.domain.EntryPolicy;
import com.dormfix.maintenance.domain.MaintenanceRequest;
import com.dormfix.maintenance.domain.RequestHistory;
import com.dormfix.maintenance.domain.RequestHistoryEventType;
import com.dormfix.maintenance.domain.RequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceRequestCreationService {
    private final MaintenanceRequestRepository requests;
    private final RequestHistoryRepository histories;
    private final UserRepository users;
    private final ResidenceRepository residences;
    private final SpaceRepository spaces;
    private final BuildingRepository buildings;
    private final DormitoryRepository dormitories;
    private final FacilityRepository facilities;
    private final MaintenanceCategoryRepository categories;
    private final Clock clock;

    public MaintenanceRequestCreationService(MaintenanceRequestRepository requests,
            RequestHistoryRepository histories, UserRepository users, ResidenceRepository residences,
            SpaceRepository spaces, BuildingRepository buildings, DormitoryRepository dormitories,
            FacilityRepository facilities, MaintenanceCategoryRepository categories, Clock clock) {
        this.requests = requests;
        this.histories = histories;
        this.users = users;
        this.residences = residences;
        this.spaces = spaces;
        this.buildings = buildings;
        this.dormitories = dormitories;
        this.facilities = facilities;
        this.categories = categories;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceRequestResult create(CreateMaintenanceRequestCommand command, Long actorId,
            Set<String> roles) {
        requireResident(roles);
        validatePreferredVisitTime(command);
        users.findById(actorId).orElseThrow(() -> new UserNotFoundException(actorId));

        ZoneId roomZone = validateActiveRoom(command.spaceId());
        requireCurrentResidence(actorId, command.spaceId(), roomZone);
        var category = categories.findById(command.categoryId())
                .orElseThrow(() -> new MaintenanceCategoryNotFoundException(command.categoryId()));
        if (!category.isActive()) {
            throw new MaintenanceRequestConflictException("Maintenance category is inactive.");
        }
        validateFacility(command.facilityId(), command.spaceId());

        Instant now = Instant.now(clock);
        MaintenanceRequest request = new MaintenanceRequest(actorId, command.spaceId(),
                command.facilityId(), command.categoryId(), command.title(), command.description(),
                category.getDefaultPriority(), entryPolicy(command.entryPolicy()),
                command.contactBeforeEntry(), command.preferredVisitStart(),
                command.preferredVisitEnd(), now);
        request = requests.saveAndFlush(request);
        request.assignRequestNumber();
        request = requests.saveAndFlush(request);
        histories.save(new RequestHistory(request.getId(), actorId,
                RequestHistoryEventType.REQUEST_CREATED, null, RequestStatus.REPORTED, now));
        return new MaintenanceRequestResult(request.getId(), request.getRequestNumber(),
                request.getStatus().name(), request.getPriority().name(), request.getVersion());
    }

    private void requireResident(Set<String> roles) {
        if (!roles.contains(Role.RESIDENT.name())) {
            throw new DormitoryScopeAccessDeniedException();
        }
    }

    private void validatePreferredVisitTime(CreateMaintenanceRequestCommand command) {
        boolean hasStart = command.preferredVisitStart() != null;
        boolean hasEnd = command.preferredVisitEnd() != null;
        if (hasStart != hasEnd || hasStart
                && !command.preferredVisitStart().isBefore(command.preferredVisitEnd())) {
            throw new InvalidPreferredVisitTimeException();
        }
    }

    private void requireCurrentResidence(Long actorId, Long spaceId, ZoneId roomZone) {
        LocalDate today = LocalDate.now(clock.withZone(roomZone));
        boolean current = residences.findAllByResidentIdOrderByStartDateDescIdDesc(actorId).stream()
                .anyMatch(residence -> residence.getRoomSpaceId().equals(spaceId)
                        && residence.isCurrent(today));
        if (!current) {
            throw new MaintenanceRequestConflictException(
                    "Space is outside the reporter's current residence.");
        }
    }

    private ZoneId validateActiveRoom(Long spaceId) {
        var space = spaces.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
        if (space.getType() != SpaceType.ROOM || !space.isActive()) {
            throw new MaintenanceRequestConflictException("Space is not an active room.");
        }
        var building = buildings.findById(space.getBuildingId())
                .orElseThrow(() -> new BuildingNotFoundException(space.getBuildingId()));
        if (!building.isActive()) {
            throw new MaintenanceRequestConflictException("Building is inactive.");
        }
        var dormitory = dormitories.findById(building.getDormitoryId())
                .orElseThrow(() -> new DormitoryNotFoundException(building.getDormitoryId()));
        if (!dormitory.isActive()) {
            throw new MaintenanceRequestConflictException("Dormitory is inactive.");
        }
        return ZoneId.of(dormitory.getTimezone());
    }

    private void validateFacility(Long facilityId, Long spaceId) {
        if (facilityId == null) {
            return;
        }
        var facility = facilities.findById(facilityId)
                .orElseThrow(() -> new FacilityNotFoundException(facilityId));
        if (!facility.getSpaceId().equals(spaceId) || facility.getStatus() == FacilityStatus.RETIRED) {
            throw new MaintenanceRequestConflictException(
                    "Facility is not valid for the request space.");
        }
    }

    private EntryPolicy entryPolicy(String value) {
        try {
            return EntryPolicy.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidEntryPolicyException();
        }
    }
}
