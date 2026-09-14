package com.dormfix.identity.api;

import com.dormfix.identity.application.DuplicateEmailException;
import com.dormfix.identity.application.SignupCommand;
import com.dormfix.identity.application.SignupResult;
import com.dormfix.identity.application.SignupService;
import com.dormfix.platform.api.ApiErrorWriter;
import com.dormfix.platform.api.RequestTraceFilter;
import com.dormfix.platform.config.SecurityConfiguration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SignupController.class)
@Import({SignupExceptionHandler.class, SecurityConfiguration.class,
        ApiErrorWriter.class, RequestTraceFilter.class})
class SignupControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private SignupService signupService;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void anonymousSignupReturnsCreatedResponseWithoutPassword() throws Exception {
        when(signupService.signup(any(SignupCommand.class))).thenReturn(new SignupResult(
                1L, "resident@example.com", "Resident", null, null,
                "ACTIVE", Set.of("RESIDENT"), Instant.parse("2026-09-13T00:00:00Z")));

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Resident","email":" resident@example.com ",
                                 "password":"plain-secret"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("resident@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("RESIDENT"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void invalidSignupReturnsStandardBadRequestWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/signup"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray());
        verifyNoInteractions(signupService);
    }

    @Test
    void roleOrStatusInputIsRejected() throws Exception {
        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Resident","email":"resident@example.com",
                                 "password":"plain-secret","role":"ADMIN","status":"ACTIVE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(signupService);
    }

    @Test
    void duplicateEmailReturnsConflict() throws Exception {
        when(signupService.signup(any(SignupCommand.class))).thenThrow(new DuplicateEmailException());

        mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Resident","email":"resident@example.com",
                                 "password":"plain-secret"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
