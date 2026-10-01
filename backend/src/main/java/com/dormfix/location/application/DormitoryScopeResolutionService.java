package com.dormfix.location.application;

import com.dormfix.identity.application.DormitoryScopeAuthorizationService;
import com.dormfix.identity.domain.Role;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DormitoryScopeResolutionService {
    private final FacilityScopeLookup facilities;
    private final SpaceRepository spaces;
    private final BuildingRepository buildings;
    private final DormitoryScopeAuthorizationService authorization;

    public DormitoryScopeResolutionService(FacilityScopeLookup facilities, SpaceRepository spaces,
            BuildingRepository buildings, DormitoryScopeAuthorizationService authorization) {
        this.facilities = facilities;
        this.spaces = spaces;
        this.buildings = buildings;
        this.authorization = authorization;
    }

    @Transactional(readOnly = true)
    public Long resolveDormitoryIdForSpace(Long spaceId) {
        var space = spaces.findById(spaceId).orElseThrow(() -> new SpaceNotFoundException(spaceId));
        return resolveDormitoryIdForBuilding(space.getBuildingId());
    }

    @Transactional(readOnly = true)
    public Long resolveDormitoryIdForFacility(Long facilityId) {
        Long spaceId = facilities.findSpaceIdByFacilityId(facilityId)
                .orElseThrow(() -> new FacilityNotFoundException(facilityId));
        return resolveDormitoryIdForSpace(spaceId);
    }

    @Transactional(readOnly = true)
    public void requireSpaceManageAccess(Long userId, Set<Role> roles, Long spaceId) {
        authorization.requireManageAccess(userId, roles, resolveDormitoryIdForSpace(spaceId));
    }

    @Transactional(readOnly = true)
    public void requireFacilityManageAccess(Long userId, Set<Role> roles, Long facilityId) {
        authorization.requireManageAccess(userId, roles, resolveDormitoryIdForFacility(facilityId));
    }

    private Long resolveDormitoryIdForBuilding(Long buildingId) {
        var building = buildings.findById(buildingId)
                .orElseThrow(() -> new BuildingNotFoundException(buildingId));
        return building.getDormitoryId();
    }
}
