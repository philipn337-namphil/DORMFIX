package com.dormfix;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.DormitoryScopeAccessDeniedException;
import com.dormfix.identity.application.DormitoryScopeAuthorizationService;
import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.AdminDormitoryScope;
import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import com.dormfix.location.application.DormitoryRepository;
import com.dormfix.location.domain.Dormitory;
import com.dormfix.test.TestJwtKeys;
import java.time.Instant;
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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DormitoryScopeAuthorizationIntegrationTest {
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
    private DormitoryScopeAuthorizationService authorization;
    @Autowired
    private AdminDormitoryScopeRepository scopes;
    @Autowired
    private UserRepository users;
    @Autowired
    private DormitoryRepository dormitories;

    @Test
    @Transactional
    @Rollback
    void realPostgresScopeRowsDriveAdminAuthorization() {
        User admin = saveUser("scoped-admin@example.com", Role.ADMIN);
        User unscopedAdmin = saveUser("unscoped-admin@example.com", Role.ADMIN);
        User resident = saveUser("resident@example.com", Role.RESIDENT);
        Dormitory managedDormitory = saveDormitory("Managed Dormitory");
        Dormitory otherDormitory = saveDormitory("Other Dormitory");
        scopes.save(new AdminDormitoryScope(admin.getId(), managedDormitory.getId()));

        assertThatCode(() -> authorization.requireManageAccess(
                admin.getId(), Set.of(Role.ADMIN), managedDormitory.getId()))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorization.requireManageAccess(
                admin.getId(), Set.of(Role.ADMIN), otherDormitory.getId()))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireManageAccess(
                unscopedAdmin.getId(), Set.of(Role.ADMIN), managedDormitory.getId()))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class);
        assertThatThrownBy(() -> authorization.requireManageAccess(
                resident.getId(), Set.of(Role.RESIDENT), managedDormitory.getId()))
                .isInstanceOf(DormitoryScopeAccessDeniedException.class);
        assertThatCode(() -> authorization.requireManageAccess(
                admin.getId(), Set.of(Role.SUPER_ADMIN), otherDormitory.getId()))
                .doesNotThrowAnyException();
    }

    private User saveUser(String email, Role role) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return users.save(new User(email, "hash", "User", null, null, UserStatus.ACTIVE,
                Set.of(role), now, now));
    }

    private Dormitory saveDormitory(String name) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return dormitories.save(new Dormitory(name, "1 Scope Street", "Asia/Seoul", true, now, now));
    }
}
