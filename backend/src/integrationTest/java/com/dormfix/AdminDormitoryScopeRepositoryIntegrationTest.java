package com.dormfix;

import com.dormfix.identity.application.AdminDormitoryScopeRepository;
import com.dormfix.identity.application.UserRepository;
import com.dormfix.identity.domain.AdminDormitoryScope;
import com.dormfix.identity.domain.AdminDormitoryScopeId;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminDormitoryScopeRepositoryIntegrationTest {
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
    private AdminDormitoryScopeRepository scopeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DormitoryRepository dormitoryRepository;
    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    @Rollback
    void savesQueriesAndDeletesScopeByCompositeId() {
        User user = saveAdmin("scope-query@example.com");
        Dormitory first = saveDormitory("First Dormitory");
        Dormitory second = saveDormitory("Second Dormitory");

        AdminDormitoryScope firstScope = scopeRepository.save(
                new AdminDormitoryScope(user.getId(), first.getId()));
        scopeRepository.save(new AdminDormitoryScope(user.getId(), second.getId()));
        entityManager.flush();

        assertThat(scopeRepository.existsById(new AdminDormitoryScopeId(user.getId(), first.getId())))
                .isTrue();
        assertThat(scopeRepository.findAllByUserId(user.getId()))
                .extracting(AdminDormitoryScope::getDormitoryId)
                .containsExactly(first.getId(), second.getId());

        scopeRepository.deleteById(firstScope.getId());
        entityManager.flush();

        assertThat(scopeRepository.existsById(firstScope.getId())).isFalse();
        assertThat(scopeRepository.findAllByUserId(user.getId()))
                .extracting(AdminDormitoryScope::getDormitoryId)
                .containsExactly(second.getId());
    }

    @Test
    @Transactional
    @Rollback
    void duplicateCompositeScopeIsRejectedByPostgresPrimaryKey() {
        User user = saveAdmin("scope-duplicate@example.com");
        Dormitory dormitory = saveDormitory("Duplicate Dormitory");
        scopeRepository.save(new AdminDormitoryScope(user.getId(), dormitory.getId()));
        entityManager.flush();

        assertThatThrownBy(() -> {
            entityManager.persist(new AdminDormitoryScope(user.getId(), dormitory.getId()));
            entityManager.flush();
        }).isInstanceOf(PersistenceException.class);
    }

    private User saveAdmin(String email) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return userRepository.save(new User(email, "hash", "Admin", null, null,
                UserStatus.ACTIVE, Set.of(Role.ADMIN), now, now));
    }

    private Dormitory saveDormitory(String name) {
        Instant now = Instant.parse("2026-09-18T00:00:00Z");
        return dormitoryRepository.save(new Dormitory(name, "1 Scope Street", "Asia/Seoul",
                true, now, now));
    }
}
