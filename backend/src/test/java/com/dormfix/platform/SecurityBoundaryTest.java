package com.dormfix.platform;

import com.dormfix.platform.api.ApiErrorWriter;
import com.dormfix.platform.api.RequestTraceFilter;
import com.dormfix.platform.config.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest
@Import({SecurityConfiguration.class, ApiErrorWriter.class, RequestTraceFilter.class})
class SecurityBoundaryTest {
    @Autowired
    private MockMvc mvc;

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

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void skeletonNeverPretendsBusinessAuthorizationIsImplemented() throws Exception {
        mvc.perform(post("/api/v1/maintenance-requests/1/resolve"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void nonHealthActuatorsRemainInaccessible() throws Exception {
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    }
}
