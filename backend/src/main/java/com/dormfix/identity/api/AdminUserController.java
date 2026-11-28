package com.dormfix.identity.api;

import com.dormfix.identity.application.AdminUserQueryService;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADR-030 administrator user lookup. */
@RestController
public class AdminUserController {
    private final AdminUserQueryService users;

    public AdminUserController(AdminUserQueryService users) {
        this.users = users;
    }

    @GetMapping("/api/v1/admin/users")
    public UserPageResponse list(@RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String email,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt) {
        return UserPageResponse.from(users.list(roles(jwt), role, status, email, page, size));
    }

    private Set<String> roles(Jwt jwt) {
        List<String> values = jwt.getClaimAsStringList("roles");
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
