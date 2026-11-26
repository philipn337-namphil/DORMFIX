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
import com.dormfix.maintenance.application.AttachmentService;
import com.dormfix.maintenance.application.CommentService;
import com.dormfix.maintenance.application.MaintenanceRequestAssignmentService;
import com.dormfix.maintenance.application.MaintenanceRequestCreationService;
import com.dormfix.maintenance.application.MaintenanceRequestLifecycleService;
import com.dormfix.maintenance.application.MaintenanceRequestPatchService;
import com.dormfix.maintenance.application.MaintenanceVisitService;
import com.dormfix.maintenance.application.WorkLogService;
import com.dormfix.maintenance.application.MaintenanceRequestQueryService;
import com.dormfix.maintenance.application.RequestHistoryQueryService;
import com.dormfix.notification.application.NotificationService;
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
    private CommentService commentService;
    @MockitoBean
    private AttachmentService attachmentService;
    @MockitoBean
    private RequestHistoryQueryService requestHistoryQueryService;
    @MockitoBean
    private NotificationService notificationService;
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
     * A privileged role gains no authority over a path the contract does not define. Every frozen
     * non-admin path is implemented now (notifications under ADR-029), so this points at paths the
     * hard invariants forbid outright: a status setter on a request. The deny-all default must
     * still answer them, whatever the role.
     */
    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void skeletonNeverPretendsBusinessAuthorizationIsImplemented() throws Exception {
        mvc.perform(post("/api/v1/maintenance-requests/1/status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(patch("/api/v1/maintenance-requests/1/status"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/v1/notifications"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
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
                "reopen", "reject", "mark-duplicate", "assignments", "visits", "work-logs",
                "comments", "attachments", "attachments/presigned-url")) {
            mvc.perform(post("/api/v1/maintenance-requests/1/" + command))
                    .andExpect(result -> org.assertj.core.api.Assertions
                            .assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        }
        mvc.perform(patch("/api/v1/maintenance-requests/1"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        mvc.perform(get("/api/v1/maintenance-requests/1/history"))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        for (var call : java.util.List.of(get("/api/v1/notifications"),
                get("/api/v1/notifications/unread-count"), patch("/api/v1/notifications/1/read"),
                post("/api/v1/notifications/read-all"))) {
            mvc.perform(call).andExpect(result -> org.assertj.core.api.Assertions
                    .assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        }
    }

    @Test
    void nonHealthActuatorsRemainInaccessible() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
