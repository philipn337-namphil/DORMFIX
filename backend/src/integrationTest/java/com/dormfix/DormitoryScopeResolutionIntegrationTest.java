package com.dormfix;

import com.dormfix.location.application.FacilityNotFoundException;
import com.dormfix.catalog.application.FacilityRepository;
import com.dormfix.catalog.domain.Facility;
import com.dormfix.catalog.domain.FacilityStatus;
import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.AdminDormitoryScope;
import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import com.dormfix.location.application.BuildingRepository;
import com.dormfix.location.application.DormitoryRepository;
import com.dormfix.location.application.DormitoryScopeResolutionService;
import com.dormfix.location.application.SpaceNotFoundException;
import com.dormfix.location.application.SpaceRepository;
import com.dormfix.location.domain.Building;
import com.dormfix.location.domain.Dormitory;
import com.dormfix.location.domain.Space;
import com.dormfix.location.domain.SpaceType;
import com.dormfix.test.TestJwtKeys;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DormitoryScopeResolutionIntegrationTest {
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
    private DormitoryScopeResolutionService resolution;
    @Autowired
    private AdminDormitoryScopeRepository scopes;
    @Autowired
    private UserRepository users;
    @Autowired
    private DormitoryRepository dormitories;
    @Autowired
    private BuildingRepository buildings;
    @Autowired
    private SpaceRepository spaces;
    @Autowired
    private FacilityRepository facilities;

    @Test
    @Transactional
    @Rollback
    void resolvesBothPathsAndDelegatesResolvedDormitoryToAuthorization() {
        User admin = saveUser("path-admin@example.com", Role.ADMIN);
        User unscopedAdmin = saveUser("path-unscoped@example.com", Role.ADMIN);
        Dormitory dormitory = saveDormitory();
        Building building = buildings.save(new Building(dormitory.getId(), "B1", "Building 1",
                true, NOW, NOW));
        Space space = spaces.save(new Space(building.getId(), "101", "Room 101", SpaceType.ROOM,
                1, null, true, NOW, NOW));
        Facility facility = facilities.save(new Facility(space.getId(), "Sink", "PLUMBING", null,
                FacilityStatus.ACTIVE, LocalDate.of(2026, 1, 1), null, NOW, NOW));
        scopes.save(new AdminDormitoryScope(admin.getId(), dormitory.getId()));

        assertThat(resolution.resolveDormitoryIdForSpace(space.getId())).isEqualTo(dormitory.getId());
        assertThat(resolution.resolveDormitoryIdForFacility(facility.getId())).isEqualTo(dormitory.getId());
        assertThatCode(() -> resolution.requireSpaceManageAccess(
                admin.getId(), Set.of(Role.ADMIN), space.getId())).doesNotThrowAnyException();
        assertThatCode(() -> resolution.requireFacilityManageAccess(
                admin.getId(), Set.of(Role.ADMIN), facility.getId())).doesNotThrowAnyException();
        assertThatCode(() -> resolution.requireFacilityManageAccess(
                admin.getId(), Set.of(Role.SUPER_ADMIN), facility.getId())).doesNotThrowAnyException();
        assertThatThrownBy(() -> resolution.requireSpaceManageAccess(
                unscopedAdmin.getId(), Set.of(Role.ADMIN), space.getId()))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class);
    }

    @Test
    @Transactional
    @Rollback
    void missingResourcesUseExistingNotFoundContracts() {
        assertThatThrownBy(() -> resolution.resolveDormitoryIdForFacility(999_991L))
                .isInstanceOf(FacilityNotFoundException.class);
        assertThatThrownBy(() -> resolution.resolveDormitoryIdForSpace(999_992L))
                .isInstanceOf(SpaceNotFoundException.class);
    }

    private static final Instant NOW = Instant.parse("2026-09-18T00:00:00Z");

    private User saveUser(String email, Role role) {
        return users.save(new User(email, "hash", "User", null, null, UserStatus.ACTIVE,
                Set.of(role), NOW, NOW));
    }

    private Dormitory saveDormitory() {
        return dormitories.save(new Dormitory("Scoped Dormitory", "1 Scope Street", "Asia/Seoul",
                true, NOW, NOW));
    }
}
