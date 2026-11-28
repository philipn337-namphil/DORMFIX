package com.dormfix.identity.application;

import com.dormfix.identity.application.AdminUserAccessPolicy.Lookup;
import com.dormfix.identity.domain.Role;
import com.dormfix.identity.domain.User;
import com.dormfix.identity.domain.UserStatus;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** GET /admin/users (ADR-030). Input is validated first, then the access policy chooses the query. */
@Service
public class AdminUserQueryService {
    static final int MIN_SIZE = 1;
    static final int MAX_SIZE = 100;

    private final UserDirectory users;

    public AdminUserQueryService(UserDirectory users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserPageResult list(Set<String> callerRoles, String role, String status, String email,
            int page, int size) {
        if (page < 0 || size < MIN_SIZE || size > MAX_SIZE) {
            throw new InvalidUserQueryException("Invalid pagination.");
        }
        Role roleFilter = parse(Role.class, role);
        UserStatus statusFilter = parse(UserStatus.class, status);
        String normalizedEmail = email == null || email.isBlank() ? null
                : email.trim().toLowerCase(Locale.ROOT);

        Lookup lookup = AdminUserAccessPolicy.decide(roles(callerRoles), roleFilter,
                normalizedEmail);
        return switch (lookup) {
            case ALL_USERS -> page(users.search(roleFilter, statusFilter, normalizedEmail, page,
                    size), users.count(roleFilter, statusFilter, normalizedEmail), page, size);
            case WORKERS -> page(users.search(Role.WORKER, statusFilter, null, page, size),
                    users.count(Role.WORKER, statusFilter, null), page, size);
            case RESIDENT_BY_EMAIL -> {
                List<User> found = users.findResidentByEmail(normalizedEmail)
                        .filter(user -> statusFilter == null || user.getStatus() == statusFilter)
                        .map(List::of).orElse(List.of());
                List<User> shown = page == 0 ? found : List.of();
                yield page(shown, found.size(), page, size);
            }
        };
    }

    private static UserPageResult page(List<User> rows, long total, int page, int size) {
        int totalPages = (int) Math.ceil((double) total / size);
        return new UserPageResult(rows.stream().map(AdminUserQueryService::result).toList(), page,
                size, total, totalPages);
    }

    private static UserSummaryResult result(User user) {
        return new UserSummaryResult(user.getId(), user.getName(), user.getEmail(),
                user.getRoles().stream().map(Role::name).sorted().toList(),
                user.getStatus().name());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException error) {
            throw new InvalidUserQueryException("Invalid " + type.getSimpleName() + ".");
        }
    }

    private static Set<Role> roles(Set<String> names) {
        Set<Role> roles = new LinkedHashSet<>();
        for (String name : names) {
            try {
                roles.add(Role.valueOf(name));
            } catch (IllegalArgumentException error) {
                throw new DormitoryScopeAccessDeniedException();
            }
        }
        return roles;
    }
}
