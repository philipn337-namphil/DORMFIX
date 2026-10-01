package com.dormfix.catalog.application;

import com.dormfix.catalog.domain.Facility;
import com.dormfix.catalog.domain.FacilityStatus;
import com.dormfix.catalog.domain.MaintenanceCategory;
import com.dormfix.catalog.domain.Priority;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.location.application.BuildingRepository;
import com.dormfix.location.application.DormitoryRepository;
import com.dormfix.location.application.SpaceRepository;
import com.dormfix.location.application.StructureStateConflictException;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminCatalogCommandService {
    private final FacilityRepository facilities;
    private final MaintenanceCategoryRepository categories;
    private final SpaceRepository spaces;
    private final BuildingRepository buildings;
    private final DormitoryRepository dormitories;
    private final DormitoryScopeResolutionService scopes;

    public AdminCatalogCommandService(FacilityRepository facilities, MaintenanceCategoryRepository categories,
            SpaceRepository spaces, BuildingRepository buildings, DormitoryRepository dormitories,
            DormitoryScopeResolutionService scopes) {
        this.facilities = facilities;
        this.categories = categories;
        this.spaces = spaces;
        this.buildings = buildings;
        this.dormitories = dormitories;
        this.scopes = scopes;
    }

    @Transactional
    public FacilityCommandResult createFacility(Long spaceId, CreateFacilityCommand command, Long userId,
            Set<String> roles) {
        scopes.requireSpaceManageAccess(userId, roleValues(roles), spaceId);
        requireActiveSpaceHierarchy(spaceId);
        Instant now = Instant.now();
        return facilityResult(facilities.save(new Facility(spaceId, command.name(), command.facilityType(),
                command.assetCode(), FacilityStatus.ACTIVE, command.installedAt(), command.description(), now, now)));
    }

    @Transactional
    public FacilityCommandResult updateFacility(Long facilityId, UpdateFacilityCommand command, Long userId,
            Set<String> roles) {
        scopes.requireFacilityManageAccess(userId, roleValues(roles), facilityId);
        Facility facility = facility(facilityId);
        facility.updateMetadata(command.name(), command.facilityType(), command.assetCode(), command.installedAt(),
                command.description(), Instant.now());
        return facilityResult(facilities.save(facility));
    }

    @Transactional
    public FacilityCommandResult takeFacilityOutOfService(Long facilityId, Long userId, Set<String> roles) {
        scopes.requireFacilityManageAccess(userId, roleValues(roles), facilityId);
        Facility facility = facility(facilityId);
        requireFacilityState(facility.getStatus() == FacilityStatus.ACTIVE, "INVALID_FACILITY_STATE",
                "Only an active facility can be taken out of service.");
        facility.takeOutOfService(Instant.now());
        return facilityResult(facilities.save(facility));
    }

    @Transactional
    public FacilityCommandResult reactivateFacility(Long facilityId, Long userId, Set<String> roles) {
        scopes.requireFacilityManageAccess(userId, roleValues(roles), facilityId);
        Facility facility = facility(facilityId);
        requireFacilityState(facility.getStatus() == FacilityStatus.OUT_OF_SERVICE, "INVALID_FACILITY_STATE",
                "Only an out-of-service facility can be reactivated.");
        requireActiveSpaceHierarchy(facility.getSpaceId());
        facility.reactivate(Instant.now());
        return facilityResult(facilities.save(facility));
    }

    @Transactional
    public FacilityCommandResult retireFacility(Long facilityId, Long userId, Set<String> roles) {
        scopes.requireFacilityManageAccess(userId, roleValues(roles), facilityId);
        Facility facility = facility(facilityId);
        requireFacilityState(facility.getStatus() != FacilityStatus.RETIRED, "INVALID_FACILITY_STATE",
                "A retired facility is terminal.");
        facility.retire(Instant.now());
        return facilityResult(facilities.save(facility));
    }

    @Transactional
    public MaintenanceCategoryCommandResult createCategory(CreateMaintenanceCategoryCommand command,
            Set<String> roles) {
        requireSuperAdmin(roles);
        if (command.parentId() != null) {
            requireActiveCategory(category(command.parentId()));
        }
        Instant now = Instant.now();
        return categoryResult(categories.save(new MaintenanceCategory(command.parentId(), command.code(), command.name(),
                priority(command.defaultPriority()), true, command.sortOrder(), now, now)));
    }

    @Transactional
    public MaintenanceCategoryCommandResult updateCategory(Long categoryId, UpdateMaintenanceCategoryCommand command,
            Set<String> roles) {
        requireSuperAdmin(roles);
        MaintenanceCategory category = category(categoryId);
        category.updateMetadata(command.code(), command.name(), priority(command.defaultPriority()), command.sortOrder(),
                Instant.now());
        return categoryResult(categories.save(category));
    }

    @Transactional
    public MaintenanceCategoryCommandResult deactivateCategory(Long categoryId, Set<String> roles) {
        requireSuperAdmin(roles);
        MaintenanceCategory category = category(categoryId);
        requireCategoryState(category.isActive(), "CATEGORY_ALREADY_INACTIVE", "Maintenance category is already inactive.");
        category.deactivate(Instant.now());
        return categoryResult(categories.save(category));
    }

    @Transactional
    public MaintenanceCategoryCommandResult reactivateCategory(Long categoryId, Set<String> roles) {
        requireSuperAdmin(roles);
        MaintenanceCategory category = category(categoryId);
        requireCategoryState(!category.isActive(), "CATEGORY_ALREADY_ACTIVE", "Maintenance category is already active.");
        if (category.getParentId() != null) {
            requireActiveCategory(category(category.getParentId()));
        }
        category.reactivate(Instant.now());
        return categoryResult(categories.save(category));
    }

    private void requireActiveSpaceHierarchy(Long spaceId) {
        var space = spaces.findById(spaceId)
                .orElseThrow(() -> new com.dormfix.location.application.SpaceNotFoundException(spaceId));
        if (!space.isActive()) {
            inactiveParent("Space");
        }
        var building = buildings.findById(space.getBuildingId())
                .orElseThrow(() -> new com.dormfix.location.application.BuildingNotFoundException(space.getBuildingId()));
        if (!building.isActive()) {
            inactiveParent("Building");
        }
        var dormitory = dormitories.findById(building.getDormitoryId())
                .orElseThrow(() -> new com.dormfix.location.application.DormitoryNotFoundException(
                        building.getDormitoryId()));
        if (!dormitory.isActive()) {
            inactiveParent("Dormitory");
        }
    }

    private Facility facility(Long id) {
        return facilities.findById(id).orElseThrow(() -> new com.dormfix.location.application.FacilityNotFoundException(id));
    }

    private MaintenanceCategory category(Long id) {
        return categories.findById(id).orElseThrow(() -> new MaintenanceCategoryNotFoundException(id));
    }

    private Priority priority(String value) {
        try {
            return Priority.valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new com.dormfix.location.application.StructureValidationException("Default priority is invalid.");
        }
    }

    private void requireSuperAdmin(Set<String> roles) {
        if (roles == null || !roles.contains(Role.SUPER_ADMIN.name())) {
            throw new DormitoryScopeAccessDeniedException();
        }
    }

    private Set<Role> roleValues(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        try {
            return values.stream().map(Role::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException error) {
            throw new DormitoryScopeAccessDeniedException();
        }
    }

    private void requireActiveCategory(MaintenanceCategory category) {
        requireCategoryState(category.isActive(), "INACTIVE_PARENT",
                "Maintenance category parent is inactive; child creation or reactivation is not allowed.");
    }

    private void requireFacilityState(boolean condition, String code, String message) {
        if (!condition) {
            throw new StructureStateConflictException(code, message);
        }
    }

    private void requireCategoryState(boolean condition, String code, String message) {
        if (!condition) {
            throw new StructureStateConflictException(code, message);
        }
    }

    private void inactiveParent(String parent) {
        throw new StructureStateConflictException("INACTIVE_PARENT",
                parent + " is inactive; child creation or reactivation is not allowed.");
    }

    private FacilityCommandResult facilityResult(Facility value) {
        return new FacilityCommandResult(value.getId(), value.getSpaceId(), value.getName(), value.getFacilityType(),
                value.getAssetCode(), value.getStatus().name(), value.getInstalledAt(), value.getDescription(),
                value.getCreatedAt(), value.getUpdatedAt());
    }

    private MaintenanceCategoryCommandResult categoryResult(MaintenanceCategory value) {
        return new MaintenanceCategoryCommandResult(value.getId(), value.getParentId(), value.getCode(), value.getName(),
                value.getDefaultPriority().name(), value.isActive(), value.getSortOrder(), value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
