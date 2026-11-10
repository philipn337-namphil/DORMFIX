package com.dormfix.platform;

import com.dormfix.platform.api.ApiErrorWriter;
import com.dormfix.platform.api.RequestTraceFilter;
import com.dormfix.platform.config.SecurityConfiguration;
import com.dormfix.identity.application.SignupService;
import com.dormfix.identity.application.LoginService;
import com.dormfix.identity.application.CurrentUserService;
import com.dormfix.identity.application.RefreshTokenService;
import com.dormfix.location.application.DormitoryQueryService;
import com.dormfix.location.application.AdminStructureCommandService;
import com.dormfix.catalog.application.CatalogQueryService;
import com.dormfix.catalog.application.AdminCatalogCommandService;
import com.dormfix.location.application.ResidenceCommandService;
import com.dormfix.location.application.ResidenceQueryService;
import com.dormfix.maintenance.application.MaintenanceRequestAssignmentService;
import com.dormfix.maintenance.application.MaintenanceRequestCreationService;
import com.dormfix.maintenance.application.MaintenanceRequestLifecycleService;
import com.dormfix.maintenance.application.MaintenanceRequestPatchService;
import com.dormfix.maintenance.application.MaintenanceVisitService;
import com.dormfix.maintenance.application.WorkLogService;
import com.dormfix.maintenance.application.MaintenanceRequestQueryService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({SecurityConfiguration.class, ApiErrorWriter.class, RequestTraceFilter.class})
class SecurityBoundaryTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private SignupService signupService;
    @MockitoBean
    private LoginService loginService;
    @MockitoBean
    private CurrentUserService currentUserService;
    @MockitoBean
    private RefreshTokenService refreshTokenService;
    @MockitoBean
    private DormitoryQueryService dormitoryQueryService;
    @MockitoBean
    private AdminStructureCommandService adminStructureCommandService;
    @MockitoBean
    private CatalogQueryService catalogQueryService;
    @MockitoBean
    private AdminCatalogCommandService adminCatalogCommandService;
    @MockitoBean
    private ResidenceCommandService residenceCommandService;
    @MockitoBean
    private ResidenceQueryService residenceQueryService;
    @MockitoBean
    private MaintenanceRequestCreationService maintenanceRequestCreationService;
    @MockitoBean
    private MaintenanceRequestQueryService maintenanceRequestQueryService;
    @MockitoBean
    private MaintenanceRequestAssignmentService maintenanceRequestAssignmentService;
    @MockitoBean
    private MaintenanceRequestLifecycleService maintenanceRequestLifecycleService;
    @MockitoBean
    private MaintenanceVisitService maintenanceVisitService;
    @MockitoBean
    private WorkLogService workLogService;
    @MockitoBean
    private MaintenanceRequestPatchService maintenanceRequestPatchService;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void unauthenticatedCommandsAreDeniedWithSafeErrorAndTrace() throws Exception {
        mvc.perform(post("/api/v1/maintenance-requests/1/resolve")
                        .header("X-Request-ID", "untrusted-input"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.path").value("/api/v1/maintenance-requests/1/resolve"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(result -> {
                    String id = result.getResponse().getHeader("X-Request-ID");
                    org.assertj.core.api.Assertions.assertThat(id).isNotEqualTo("untrusted-input");
                    org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                            .contains(id);
                });
    }

    /**
     * A privileged role gains no authority over a frozen path that is not implemented yet. The
     * lifecycle commands this test used to target are now implemented under ADR-022, so it points
     * at the paths that are still unimplemented: comments and attachments. Their authorization is
     * covered by their own slices once they exist.
     */
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void skeletonNeverPretendsBusinessAuthorizationIsImplemented() throws Exception {
        for (String unimplemented : java.util.List.of(
                "/api/v1/maintenance-requests/1/comments",
                "/api/v1/maintenance-requests/1/attachments")) {
            mvc.perform(post(unimplemented))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    /**
     * The implemented lifecycle commands are now reachable for an authenticated caller, so the
     * deny-all default no longer hides them. Business authorization for them lives in
     * MaintenanceRequestLifecycleIntegrationTest against a real database.
     */
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void implementedLifecyclePathsAreNoLongerBlockedByTheDenyAllDefault() throws Exception {
        for (String command : java.util.List.of("start", "hold", "resume", "resolve", "close",
                "reopen", "reject", "mark-duplicate", "assignments", "visits", "work-logs")) {
            mvc.perform(post("/api/v1/maintenance-requests/1/" + command))
                    .andExpect(result -> org.assertj.core.api.Assertions
                            .assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        }
        mvc.perform(patch("/api/v1/maintenance-requests/1"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }

    @Test
    void nonHealthActuatorsRemainInaccessible() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
