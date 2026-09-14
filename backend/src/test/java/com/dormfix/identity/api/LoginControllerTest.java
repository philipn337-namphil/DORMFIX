package com.dormfix.identity.api;

import com.dormfix.identity.application.LoginCommand;
import com.dormfix.identity.application.LoginRejectedException;
import com.dormfix.identity.application.LoginResult;
import com.dormfix.identity.application.LoginService;
import com.dormfix.platform.api.ApiErrorWriter;
import com.dormfix.platform.api.RequestTraceFilter;
import com.dormfix.platform.config.SecurityConfiguration;
import java.time.Instant;
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

@WebMvcTest(LoginController.class)
@Import({LoginExceptionHandler.class, SecurityConfiguration.class,
        ApiErrorWriter.class, RequestTraceFilter.class})
class LoginControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private LoginService loginService;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @Test
    void anonymousLoginReturnsAccessToken() throws Exception {
        when(loginService.login(any(LoginCommand.class))).thenReturn(new LoginResult(
                "signed-jwt", "Bearer", Instant.parse("2026-09-13T00:30:00Z"),
                "opaque-refresh-token", Instant.parse("2026-09-27T00:00:00Z")));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" resident@example.com ","password":"plain-secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-jwt"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-09-13T00:30:00Z"))
                .andExpect(jsonPath("$.refreshToken").value("opaque-refresh-token"))
                .andExpect(jsonPath("$.refreshTokenExpiresAt").value("2026-09-27T00:00:00Z"));
    }

    @Test
    void invalidLoginReturnsStandardBadRequestWithoutCallingService() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").isArray());
        verifyNoInteractions(loginService);
    }

    @Test
    void rejectedLoginReturnsGenericUnauthorizedError() throws Exception {
        when(loginService.login(any(LoginCommand.class))).thenThrow(new LoginRejectedException());

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"resident@example.com","password":"wrong-secret"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Email or password is invalid."))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
