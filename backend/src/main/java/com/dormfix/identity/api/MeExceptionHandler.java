package com.dormfix.identity.api;

import com.dormfix.identity.application.CurrentUserNotFoundException;
import com.dormfix.platform.api.ApiError;
import com.dormfix.platform.api.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MeController.class)
public class MeExceptionHandler {
    @ExceptionHandler({CurrentUserNotFoundException.class, IllegalArgumentException.class})
    ResponseEntity<ApiError> invalidAuthentication(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiError(Instant.now(), 401,
                "AUTHENTICATION_REQUIRED", "Authentication is required.", request.getRequestURI(),
                (String) request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE), List.of()));
    }
}
