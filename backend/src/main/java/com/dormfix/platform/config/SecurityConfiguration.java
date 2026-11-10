package com.dormfix.platform.config;

import com.dormfix.platform.api.ApiErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errors,
            AuthenticationEntryPoint authenticationEntryPoint,
            JwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness",
                                "/actuator/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me/residences").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/maintenance-requests").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/maintenance-requests",
                                "/api/v1/maintenance-requests/assigned-to-me",
                                "/api/v1/maintenance-requests/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/maintenance-requests/*/assignments",
                                "/api/v1/maintenance-requests/*/start",
                                "/api/v1/maintenance-requests/*/hold",
                                "/api/v1/maintenance-requests/*/resume",
                                "/api/v1/maintenance-requests/*/resolve",
                                "/api/v1/maintenance-requests/*/close",
                                "/api/v1/maintenance-requests/*/reopen",
                                "/api/v1/maintenance-requests/*/reject",
                                "/api/v1/maintenance-requests/*/mark-duplicate",
                                "/api/v1/maintenance-requests/*/visits",
                                "/api/v1/visits/*/complete",
                                "/api/v1/visits/*/no-access",
                                "/api/v1/visits/*/cancel",
                                "/api/v1/maintenance-requests/*/work-logs",
                                "/api/v1/maintenance-requests/*/comments",
                                "/api/v1/maintenance-requests/*/attachments",
                                "/api/v1/maintenance-requests/*/attachments/presigned-url")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/maintenance-requests/*/visits",
                                "/api/v1/maintenance-requests/*/work-logs",
                                "/api/v1/maintenance-requests/*/comments",
                                "/api/v1/maintenance-requests/*/attachments",
                                "/api/v1/attachments/*/download-url")
                        .authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/comments/*").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/visits/*",
                                "/api/v1/maintenance-requests/*")
                        .authenticated()
                        .requestMatchers("/api/v1/admin/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/dormitories",
                                "/api/v1/dormitories/*/buildings", "/api/v1/buildings/*/spaces",
                                "/api/v1/spaces/*", "/api/v1/spaces/*/facilities", "/api/v1/categories")
                        .authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler((request, response, error) -> errors.write(request,
                                response, 403, "ACCESS_DENIED", "Access is denied.")))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    @Bean
    AuthenticationEntryPoint authenticationEntryPoint(ApiErrorWriter errors) {
        return (request, response, error) -> errors.write(request, response, 401,
                "AUTHENTICATION_REQUIRED", "Authentication is required.");
    }
}
