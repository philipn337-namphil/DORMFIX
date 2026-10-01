package com.dormfix.location.application;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.application.UserNotFoundException;
import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.domain.Residence;
import com.dormfix.location.domain.SpaceType;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResidenceCommandService {
    private final ResidenceRepository residences;
    private final UserRepository users;
    private final SpaceRepository spaces;
    private final BuildingRepository buildings;
    private final DormitoryRepository dormitories;
    private final DormitoryScopeResolutionService scopeResolution;
    private final AdminDormitoryScopeRepository adminScopes;

    public ResidenceCommandService(ResidenceRepository residences, UserRepository users,
            SpaceRepository spaces, BuildingRepository buildings, DormitoryRepository dormitories,
            DormitoryScopeResolutionService scopeResolution,
            AdminDormitoryScopeRepository adminScopes) {
        this.residences = residences;
        this.users = users;
        this.spaces = spaces;
        this.buildings = buildings;
        this.dormitories = dormitories;
        this.scopeResolution = scopeResolution;
        this.adminScopes = adminScopes;
    }

    @Transactional
    public ResidenceResult create(Long residentId, Long roomSpaceId, LocalDate startDate,
            Long actorId, Set<String> roleNames) {
        scopeResolution.requireSpaceManageAccess(actorId, roleValues(roleNames), roomSpaceId);
        validateResident(residentId);
        ZoneId zone = validateActiveRoom(roomSpaceId);
        if (startDate.isAfter(LocalDate.now(Clock.system(zone)))) {
            throw new StructureValidationException("Residence startDate cannot be in the future.");
        }
        if (residences.existsOverlapping(residentId, startDate, null)) {
            throw conflict("RESIDENCE_PERIOD_OVERLAP", "Residence period overlaps an existing residence.");
        }
        return result(residences.save(new Residence(residentId, roomSpaceId, startDate, null, Instant.now())));
    }

    @Transactional
    public ResidenceResult end(Long id, Long actorId, Set<String> roleNames) {
        Residence residence = find(id);
        scopeResolution.requireSpaceManageAccess(actorId, roleValues(roleNames), residence.getRoomSpaceId());
        LocalDate today = LocalDate.now(Clock.system(validateActiveRoom(residence.getRoomSpaceId())));
        if (!residence.isCurrent(today)) {
            throw conflict("RESIDENCE_NOT_CURRENT", "Residence is not current.");
        }
        if (!residence.getStartDate().isBefore(today)) {
            throw conflict("RESIDENCE_SAME_DAY_END", "Residence cannot end on its start date.");
        }
        residence.end(today);
        return result(residences.save(residence));
    }

    @Transactional(readOnly = true)
    public ResidenceResult findForAdmin(Long id, Long actorId, Set<String> roleNames) {
        Residence residence = find(id);
        scopeResolution.requireSpaceManageAccess(actorId, roleValues(roleNames), residence.getRoomSpaceId());
        return result(residence);
    }

    @Transactional(readOnly = true)
    public ResidencePageResult listForAdmin(Long residentId, Long roomSpaceId, Boolean current,
            int page, int size, Long actorId, Set<String> roleNames) {
        validatePage(page, size);
        if (residentId == null && roomSpaceId == null && current == null) {
            throw new StructureValidationException("At least one residence filter is required.");
        }
        Predicate<Residence> visibility = administratorVisibility(actorId, roleValues(roleNames), roomSpaceId);
        List<Residence> candidates = roomSpaceId != null
                ? residences.findAllByRoomSpaceIdOrderByStartDateDescIdDesc(roomSpaceId)
                : residentId != null ? residences.findAllByResidentIdOrderByStartDateDescIdDesc(residentId)
                : residences.findAllByOrderByStartDateDescIdDesc();
        return page(candidates.stream().filter(visibility)
                .filter(value -> current == null || isCurrent(value) == current).toList(), page, size);
    }

    @Transactional(readOnly = true)
    public ResidencePageResult own(Long userId, Set<String> roleNames, Boolean current, int page, int size) {
        validatePage(page, size);
        if (!roleNames.contains(Role.RESIDENT.name())) {
            throw new DormitoryScopeAccessDeniedException();
        }
        List<Residence> values = residences.findAllByResidentIdOrderByStartDateDescIdDesc(userId).stream()
                .filter(value -> current == null || isCurrent(value) == current).toList();
        return page(values, page, size);
    }

    private Predicate<Residence> administratorVisibility(Long actorId, Set<Role> roles, Long roomSpaceId) {
        if (roles.contains(Role.SUPER_ADMIN)) {
            return value -> true;
        }
        if (!roles.contains(Role.ADMIN)) {
            throw new DormitoryScopeAccessDeniedException();
        }
        Set<Long> scopeIds = adminScopes.findAllByUserId(actorId).stream().map(scope -> scope.getDormitoryId())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (scopeIds.isEmpty()) {
            throw new DormitoryScopeAccessDeniedException();
        }
        if (roomSpaceId != null && !scopeIds.contains(scopeResolution.resolveDormitoryIdForSpace(roomSpaceId))) {
            throw new DormitoryScopeAccessDeniedException();
        }
        return value -> scopeIds.contains(scopeResolution.resolveDormitoryIdForSpace(value.getRoomSpaceId()));
    }

    private ResidencePageResult page(List<Residence> values, int page, int size) {
        int from = Math.min(page * size, values.size());
        int to = Math.min(from + size, values.size());
        int totalPages = values.isEmpty() ? 0 : (int) Math.ceil((double) values.size() / size);
        return new ResidencePageResult(values.subList(from, to).stream().map(this::result).toList(),
                page, size, values.size(), totalPages);
    }

    private boolean isCurrent(Residence value) {
        return value.isCurrent(LocalDate.now(Clock.system(roomZone(value.getRoomSpaceId()))));
    }

    private Residence find(Long id) {
        return residences.findById(id).orElseThrow(() -> new ResidenceNotFoundException(id));
    }

    private void validateResident(Long id) {
        var user = users.findById(id).orElseThrow(() -> new UserNotFoundException(id));
        if (!user.getRoles().contains(Role.RESIDENT)) {
            throw conflict("USER_NOT_RESIDENT", "User is not a resident.");
        }
    }

    private ZoneId validateActiveRoom(Long id) {
        var space = spaces.findById(id).orElseThrow(() -> new SpaceNotFoundException(id));
        if (space.getType() != SpaceType.ROOM) throw conflict("SPACE_NOT_ROOM", "Space is not a room.");
        if (!space.isActive()) throw conflict("INACTIVE_PARENT", "Space is inactive.");
        var building = buildings.findById(space.getBuildingId())
                .orElseThrow(() -> new BuildingNotFoundException(space.getBuildingId()));
        if (!building.isActive()) throw conflict("INACTIVE_PARENT", "Building is inactive.");
        var dormitory = dormitories.findById(building.getDormitoryId())
                .orElseThrow(() -> new DormitoryNotFoundException(building.getDormitoryId()));
        if (!dormitory.isActive()) throw conflict("INACTIVE_PARENT", "Dormitory is inactive.");
        return ZoneId.of(dormitory.getTimezone());
    }

    private ZoneId roomZone(Long roomSpaceId) {
        var space = spaces.findById(roomSpaceId).orElseThrow(() -> new SpaceNotFoundException(roomSpaceId));
        var building = buildings.findById(space.getBuildingId())
                .orElseThrow(() -> new BuildingNotFoundException(space.getBuildingId()));
        var dormitory = dormitories.findById(building.getDormitoryId())
                .orElseThrow(() -> new DormitoryNotFoundException(building.getDormitoryId()));
        return ZoneId.of(dormitory.getTimezone());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new StructureValidationException("Invalid pagination.");
    }

    private StructureStateConflictException conflict(String code, String message) {
        return new StructureStateConflictException(code, message);
    }

    private Set<Role> roleValues(Set<String> values) {
        try {
            return values.stream().map(Role::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException exception) {
            throw new DormitoryScopeAccessDeniedException();
        }
    }

    private ResidenceResult result(Residence value) {
        return new ResidenceResult(value.getId(), value.getResidentId(), value.getRoomSpaceId(),
                value.getStartDate(), value.getEndDate(), value.getCreatedAt());
    }
}
