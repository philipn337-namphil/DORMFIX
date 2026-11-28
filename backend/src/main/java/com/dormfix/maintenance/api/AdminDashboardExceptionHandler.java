package com.dormfix.maintenance.api;

import com.dormfix.location.application.DormitoryNotFoundException;
import com.dormfix.platform.api.ApiError;
import com.dormfix.platform.api.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = AdminDashboardController.class)
public class AdminDashboardExceptionHandler {
    @ExceptionHandler(DormitoryNotFoundException.class)
    ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return response(request, HttpStatus.NOT_FOUND, "DORMITORY_NOT_FOUND",
                "Resource was not found.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The request contains invalid fields.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> forbidden(HttpServletRequest request) {
        return response(request, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access is denied.");
    }

    /**
     * Required by the standard error contract: without these, an unmapped failure would return a
     * container default body with no code, traceId or fieldErrors.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> dataIntegrity(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "INVALID_REQUEST_STATE",
                "The request conflicts with the current data state.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(HttpServletRequest request) {
        return response(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The request could not be completed.");
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, HttpStatus status,
            String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code,
                message, request.getRequestURI(),
                (String) request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE), List.of()));
    }
}
