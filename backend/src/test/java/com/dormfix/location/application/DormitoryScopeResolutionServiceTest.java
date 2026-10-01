package com.dormfix.location.application;

import com.dormfix.location.application.FacilityNotFoundException;
import com.dormfix.identity.application.DormitoryScopeAuthorizationService;
import com.dormfix.identity.domain.Role;
import com.dormfix.location.domain.Building;
import com.dormfix.location.domain.Space;
import com.dormfix.location.domain.SpaceType;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DormitoryScopeResolutionServiceTest {
    private static final Long USER_ID = 10L;
    private static final Long DORMITORY_ID = 20L;
    private static final Long BUILDING_ID = 30L;
    private static final Long SPACE_ID = 40L;
    private static final Long FACILITY_ID = 50L;
    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    @Mock
    private FacilityScopeLookup facilities;
    @Mock
    private SpaceRepository spaces;
    @Mock
    private BuildingRepository buildings;
    @Mock
    private DormitoryScopeAuthorizationService authorization;

    private DormitoryScopeResolutionService service;

    @BeforeEach
    void setUp() {
        service = new DormitoryScopeResolutionService(facilities, spaces, buildings, authorization);
    }

    @Test
    void resolvesDormitoryThroughSpaceBuilding() {
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space()));
        when(buildings.findById(BUILDING_ID)).thenReturn(Optional.of(building()));

        assertThat(service.resolveDormitoryIdForSpace(SPACE_ID)).isEqualTo(DORMITORY_ID);
    }

    @Test
    void resolvesDormitoryThroughFacilitySpaceBuilding() {
        when(facilities.findSpaceIdByFacilityId(FACILITY_ID)).thenReturn(Optional.of(SPACE_ID));
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space()));
        when(buildings.findById(BUILDING_ID)).thenReturn(Optional.of(building()));

        assertThat(service.resolveDormitoryIdForFacility(FACILITY_ID)).isEqualTo(DORMITORY_ID);
    }

    @Test
    void passesResolvedSpaceDormitoryToExistingAuthorizationService() {
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space()));
        when(buildings.findById(BUILDING_ID)).thenReturn(Optional.of(building()));

        service.requireSpaceManageAccess(USER_ID, Set.of(Role.ADMIN), SPACE_ID);

        verify(authorization).requireManageAccess(USER_ID, Set.of(Role.ADMIN), DORMITORY_ID);
    }

    @Test
    void passesResolvedFacilityDormitoryToExistingAuthorizationService() {
        when(facilities.findSpaceIdByFacilityId(FACILITY_ID)).thenReturn(Optional.of(SPACE_ID));
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space()));
        when(buildings.findById(BUILDING_ID)).thenReturn(Optional.of(building()));

        service.requireFacilityManageAccess(USER_ID, Set.of(Role.ADMIN), FACILITY_ID);

        verify(authorization).requireManageAccess(USER_ID, Set.of(Role.ADMIN), DORMITORY_ID);
    }

    @Test
    void missingFacilityUsesExistingFacilityNotFoundContract() {
        when(facilities.findSpaceIdByFacilityId(FACILITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveDormitoryIdForFacility(FACILITY_ID))
                .isInstanceOf(FacilityNotFoundException.class);
    }

    @Test
    void missingIntermediateSpaceOrBuildingUsesExistingNotFoundContracts() {
        when(facilities.findSpaceIdByFacilityId(FACILITY_ID)).thenReturn(Optional.of(SPACE_ID));
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveDormitoryIdForFacility(FACILITY_ID))
                .isInstanceOf(SpaceNotFoundException.class);

        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space()));
        when(buildings.findById(BUILDING_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveDormitoryIdForSpace(SPACE_ID))
                .isInstanceOf(BuildingNotFoundException.class);
    }

    private Space space() {
        return new Space(BUILDING_ID, "101", "Room 101", SpaceType.ROOM, 1,
                null, true, NOW, NOW);
    }

    private Building building() {
        return new Building(DORMITORY_ID, "B1", "Building 1", true, NOW, NOW);
    }

}
