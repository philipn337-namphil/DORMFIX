package com.dormfix.location.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.location.domain.Building;
import com.dormfix.location.domain.Dormitory;
import com.dormfix.location.domain.Space;
import com.dormfix.location.domain.SpaceType;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminStructureCommandService {
    private final DormitoryRepository dormitories;
    private final BuildingRepository buildings;
    private final SpaceRepository spaces;

    public AdminStructureCommandService(DormitoryRepository dormitories, BuildingRepository buildings,
            SpaceRepository spaces) {
        this.dormitories = dormitories;
        this.buildings = buildings;
        this.spaces = spaces;
    }

    @Transactional
    public DormitoryQueryResult createDormitory(CreateDormitoryCommand command, Set<String> roles) {
        requireSuperAdmin(roles);
        Instant now = Instant.now();
        return dormitoryResult(dormitories.save(
                new Dormitory(command.name(), command.address(), command.timezone(), true, now, now)));
    }

    @Transactional
    public DormitoryQueryResult updateDormitory(Long dormitoryId, UpdateDormitoryCommand command,
            Set<String> roles) {
        requireSuperAdmin(roles);
        Dormitory dormitory = findDormitory(dormitoryId);
        dormitory.updateDetails(command.name(), command.address(), command.timezone(), Instant.now());
        return dormitoryResult(dormitories.save(dormitory));
    }

    @Transactional
    public DormitoryQueryResult deactivateDormitory(Long dormitoryId, Set<String> roles) {
        requireSuperAdmin(roles);
        Dormitory dormitory = findDormitory(dormitoryId);
        requireActive(dormitory.isActive(), "DORMITORY_ALREADY_INACTIVE", "Dormitory is already inactive.");
        dormitory.deactivate(Instant.now());
        return dormitoryResult(dormitories.save(dormitory));
    }

    @Transactional
    public DormitoryQueryResult reactivateDormitory(Long dormitoryId, Set<String> roles) {
        requireSuperAdmin(roles);
        Dormitory dormitory = findDormitory(dormitoryId);
        requireInactive(dormitory.isActive(), "DORMITORY_ALREADY_ACTIVE", "Dormitory is already active.");
        dormitory.reactivate(Instant.now());
        return dormitoryResult(dormitories.save(dormitory));
    }

    @Transactional
    public BuildingQueryResult createBuilding(Long dormitoryId, CreateBuildingCommand command, Set<String> roles) {
        requireSuperAdmin(roles);
        Dormitory dormitory = findDormitory(dormitoryId);
        requireParentActive(dormitory.isActive(), "Dormitory");
        Instant now = Instant.now();
        return buildingResult(buildings.save(
                new Building(dormitoryId, command.code(), command.name(), true, now, now)));
    }

    @Transactional
    public BuildingQueryResult updateBuilding(Long buildingId, UpdateBuildingCommand command, Set<String> roles) {
        requireSuperAdmin(roles);
        Building building = findBuilding(buildingId);
        building.updateDetails(command.code(), command.name(), Instant.now());
        return buildingResult(buildings.save(building));
    }

    @Transactional
    public BuildingQueryResult deactivateBuilding(Long buildingId, Set<String> roles) {
        requireSuperAdmin(roles);
        Building building = findBuilding(buildingId);
        requireActive(building.isActive(), "BUILDING_ALREADY_INACTIVE", "Building is already inactive.");
        building.deactivate(Instant.now());
        return buildingResult(buildings.save(building));
    }

    @Transactional
    public BuildingQueryResult reactivateBuilding(Long buildingId, Set<String> roles) {
        requireSuperAdmin(roles);
        Building building = findBuilding(buildingId);
        requireInactive(building.isActive(), "BUILDING_ALREADY_ACTIVE", "Building is already active.");
        Dormitory dormitory = findDormitory(building.getDormitoryId());
        requireParentActive(dormitory.isActive(), "Dormitory");
        building.reactivate(Instant.now());
        return buildingResult(buildings.save(building));
    }

    @Transactional
    public SpaceQueryResult createSpace(Long buildingId, CreateSpaceCommand command, Set<String> roles) {
        requireSuperAdmin(roles);
        Building building = findBuilding(buildingId);
        requireParentActive(building.isActive(), "Building");
        Dormitory dormitory = findDormitory(building.getDormitoryId());
        requireParentActive(dormitory.isActive(), "Dormitory");
        Instant now = Instant.now();
        return spaceResult(spaces.save(new Space(buildingId, command.code(), command.name(), spaceType(command.type()),
                command.floor(), command.description(), true, now, now)));
    }

    @Transactional
    public SpaceQueryResult updateSpace(Long spaceId, UpdateSpaceCommand command, Set<String> roles) {
        requireSuperAdmin(roles);
        Space space = findSpace(spaceId);
        space.updateDetails(command.code(), command.name(), spaceType(command.type()), command.floor(), command.description(),
                Instant.now());
        return spaceResult(spaces.save(space));
    }

    @Transactional
    public SpaceQueryResult deactivateSpace(Long spaceId, Set<String> roles) {
        requireSuperAdmin(roles);
        Space space = findSpace(spaceId);
        requireActive(space.isActive(), "SPACE_ALREADY_INACTIVE", "Space is already inactive.");
        space.deactivate(Instant.now());
        return spaceResult(spaces.save(space));
    }

    @Transactional
    public SpaceQueryResult reactivateSpace(Long spaceId, Set<String> roles) {
        requireSuperAdmin(roles);
        Space space = findSpace(spaceId);
        requireInactive(space.isActive(), "SPACE_ALREADY_ACTIVE", "Space is already active.");
        Building building = findBuilding(space.getBuildingId());
        requireParentActive(building.isActive(), "Building");
        Dormitory dormitory = findDormitory(building.getDormitoryId());
        requireParentActive(dormitory.isActive(), "Dormitory");
        space.reactivate(Instant.now());
        return spaceResult(spaces.save(space));
    }

    private void requireSuperAdmin(Set<String> roles) {
        if (roles == null || !roles.contains("SUPER_ADMIN")) {
            throw new DormitoryScopeAccessDeniedException();
        }
    }

    private Dormitory findDormitory(Long id) {
        return dormitories.findById(id).orElseThrow(() -> new DormitoryNotFoundException(id));
    }

    private Building findBuilding(Long id) {
        return buildings.findById(id).orElseThrow(() -> new BuildingNotFoundException(id));
    }

    private Space findSpace(Long id) {
        return spaces.findById(id).orElseThrow(() -> new SpaceNotFoundException(id));
    }

    private void requireParentActive(boolean active, String parentType) {
        if (!active) {
            throw new StructureStateConflictException("INACTIVE_PARENT",
                    parentType + " is inactive; child creation or reactivation is not allowed.");
        }
    }

    private SpaceType spaceType(String value) {
        try {
            return SpaceType.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new StructureValidationException("Space type is invalid.");
        }
    }

    private void requireActive(boolean active, String code, String message) {
        if (!active) {
            throw new StructureStateConflictException(code, message);
        }
    }

    private void requireInactive(boolean active, String code, String message) {
        if (active) {
            throw new StructureStateConflictException(code, message);
        }
    }

    private DormitoryQueryResult dormitoryResult(Dormitory value) {
        return new DormitoryQueryResult(value.getId(), value.getName(), value.getAddress(), value.getTimezone(),
                value.isActive(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private BuildingQueryResult buildingResult(Building value) {
        return new BuildingQueryResult(value.getId(), value.getDormitoryId(), value.getCode(), value.getName(),
                value.isActive(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private SpaceQueryResult spaceResult(Space value) {
        return new SpaceQueryResult(value.getId(), value.getBuildingId(), value.getCode(), value.getName(),
                value.getType().name(), value.getFloor(), value.getDescription(), value.isActive(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}
