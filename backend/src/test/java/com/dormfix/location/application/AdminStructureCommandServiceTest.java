package com.dormfix.location.application;

import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.location.domain.Building;
import com.dormfix.location.domain.Dormitory;
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
class AdminStructureCommandServiceTest {
    private static final Long DORMITORY_ID = 1L;
    private static final Long BUILDING_ID = 2L;
    private static final Long SPACE_ID = 3L;
    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    @Mock
    private DormitoryRepository dormitories;
    @Mock
    private BuildingRepository buildings;
    @Mock
    private SpaceRepository spaces;

    private AdminStructureCommandService service;

    @BeforeEach
    void setUp() {
        service = new AdminStructureCommandService(dormitories, buildings, spaces);
    }

    @Test
    void onlySuperAdminCanCreateStructure() {
        assertThatThrownBy(() -> service.createDormitory(
                new CreateDormitoryCommand("North", "1 Main Street", "Asia/Seoul"), Set.of("ADMIN")))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class);
    }

    @Test
    void childCreationBelowInactiveParentIsAConflict() {
        Dormitory dormitory = dormitory(false);
        when(dormitories.findById(DORMITORY_ID)).thenReturn(Optional.of(dormitory));

        assertThatThrownBy(() -> service.createBuilding(DORMITORY_ID,
                new CreateBuildingCommand("N1", "North 1"), Set.of("SUPER_ADMIN")))
                .isInstanceOfSatisfying(StructureStateConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INACTIVE_PARENT"));
    }

    @Test
    void spaceReactivationRequiresActiveBuildingAndDormitory() {
        Space space = new Space(BUILDING_ID, "101", "Room 101", SpaceType.ROOM, 1,
                null, false, NOW, NOW);
        when(spaces.findById(SPACE_ID)).thenReturn(Optional.of(space));
        when(buildings.findById(BUILDING_ID)).thenReturn(Optional.of(building(true)));
        when(dormitories.findById(DORMITORY_ID)).thenReturn(Optional.of(dormitory(false)));

        assertThatThrownBy(() -> service.reactivateSpace(SPACE_ID, Set.of("SUPER_ADMIN")))
                .isInstanceOfSatisfying(StructureStateConflictException.class,
                        error -> assertThat(error.getCode()).isEqualTo("INACTIVE_PARENT"));
    }

    @Test
    void deactivationAndReactivationAreNamedCommands() {
        Dormitory dormitory = dormitory(true);
        when(dormitories.findById(DORMITORY_ID)).thenReturn(Optional.of(dormitory));
        when(dormitories.save(dormitory)).thenReturn(dormitory);

        service.deactivateDormitory(DORMITORY_ID, Set.of("SUPER_ADMIN"));
        assertThat(dormitory.isActive()).isFalse();
        service.reactivateDormitory(DORMITORY_ID, Set.of("SUPER_ADMIN"));
        assertThat(dormitory.isActive()).isTrue();
        verify(dormitories, org.mockito.Mockito.times(2)).save(dormitory);
    }

    private Dormitory dormitory(boolean active) {
        return new Dormitory("North", "1 Main Street", "Asia/Seoul", active, NOW, NOW);
    }

    private Building building(boolean active) {
        return new Building(DORMITORY_ID, "N1", "North 1", active, NOW, NOW);
    }
}
