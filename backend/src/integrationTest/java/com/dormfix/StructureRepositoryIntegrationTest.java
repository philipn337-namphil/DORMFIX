package com.dormfix;

import com.dormfix.catalog.application.FacilityRepository;
import com.dormfix.catalog.application.MaintenanceCategoryRepository;
import com.dormfix.catalog.domain.Facility;
import com.dormfix.catalog.domain.FacilityStatus;
import com.dormfix.catalog.domain.MaintenanceCategory;
import com.dormfix.catalog.domain.Priority;
import com.dormfix.location.application.BuildingRepository;
import com.dormfix.location.application.DormitoryRepository;
import com.dormfix.location.application.SpaceRepository;
import com.dormfix.location.domain.Building;
import com.dormfix.location.domain.Dormitory;
import com.dormfix.location.domain.Space;
import com.dormfix.location.domain.SpaceType;
import com.dormfix.test.TestJwtKeys;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StructureRepositoryIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17.6-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        TestJwtKeys.register(registry);
    }

    @Autowired
    private DormitoryRepository dormitoryRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private SpaceRepository spaceRepository;
    @Autowired
    private FacilityRepository facilityRepository;
    @Autowired
    private MaintenanceCategoryRepository categoryRepository;

    @Test
    @Transactional
    @Rollback
    void repositoriesSaveAndQueryStructureInExpectedOrder() {
        Instant now = Instant.parse("2026-09-16T00:00:00Z");
        Dormitory dormitory = dormitoryRepository.save(
                new Dormitory("North Dormitory", "1 Main Street", "Asia/Seoul", true, now, now));
        dormitoryRepository.save(
                new Dormitory("Hidden Dormitory", "2 Main Street", "Asia/Seoul", false, now, now));
        Dormitory secondDormitory = dormitoryRepository.save(
                new Dormitory("Alpha Dormitory", "3 Main Street", "Asia/Seoul", true, now, now));

        assertThat(dormitoryRepository.findAllByActiveTrueOrderByNameAsc())
                .extracting(Dormitory::getName)
                .containsExactly("Alpha Dormitory", "North Dormitory");

        Building building = buildingRepository.save(
                new Building(dormitory.getId(), "B2", "Second Building", true, now, now));
        buildingRepository.save(
                new Building(dormitory.getId(), "B1", "First Building", true, now, now));
        buildingRepository.save(
                new Building(dormitory.getId(), "B3", "Inactive Building", false, now, now));
        buildingRepository.save(
                new Building(secondDormitory.getId(), "A1", "Alpha Building", true, now, now));

        assertThat(buildingRepository.findAllByDormitoryIdAndActiveTrueOrderByCodeAsc(dormitory.getId()))
                .extracting(Building::getCode)
                .containsExactly("B1", "B2");

        Space space = spaceRepository.save(
                new Space(building.getId(), "102", "Room 102", SpaceType.ROOM, 1,
                        null, true, now, now));
        spaceRepository.save(
                new Space(building.getId(), "101", "Room 101", SpaceType.ROOM, 1,
                        null, true, now, now));
        spaceRepository.save(
                new Space(building.getId(), "103", "Inactive Room", SpaceType.ROOM, 1,
                        null, false, now, now));

        assertThat(spaceRepository.findById(space.getId())).isPresent()
                .get().extracting(Space::getCode).isEqualTo("102");
        assertThat(spaceRepository.findAllByBuildingIdAndActiveTrueOrderByCodeAsc(building.getId()))
                .extracting(Space::getCode)
                .containsExactly("101", "102");

        facilityRepository.save(new Facility(space.getId(), "Water Heater", "PLUMBING", null,
                FacilityStatus.OUT_OF_SERVICE, LocalDate.of(2026, 1, 1), null, now, now));
        facilityRepository.save(new Facility(space.getId(), "Air Conditioner", "HVAC", "AC-1",
                FacilityStatus.ACTIVE, null, null, now, now));
        assertThat(facilityRepository.findAllBySpaceIdOrderByNameAsc(space.getId()))
                .extracting(Facility::getName)
                .containsExactly("Air Conditioner", "Water Heater");

        MaintenanceCategory root = categoryRepository.save(new MaintenanceCategory(null, "PLUMBING",
                "Plumbing", Priority.NORMAL, true, 2, now, now));
        categoryRepository.save(new MaintenanceCategory(root.getId(), "WATER",
                "Water", Priority.HIGH, true, 1, now, now));
        categoryRepository.save(new MaintenanceCategory(null, "RETIRED",
                "Retired", Priority.LOW, false, 0, now, now));
        assertThat(categoryRepository.findAllByActiveTrueOrderBySortOrderAscCodeAsc())
                .extracting(MaintenanceCategory::getCode)
                .containsExactly("WATER", "PLUMBING");
        assertThat(categoryRepository.findAllByActiveTrueOrderBySortOrderAscCodeAsc())
                .extracting(MaintenanceCategory::getParentId)
                .last().isNull();
    }
}
