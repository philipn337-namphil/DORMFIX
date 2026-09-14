package com.dormfix.identity.api;

import com.dormfix.identity.application.DuplicateEmailException;
import com.dormfix.platform.api.ApiError;
import com.dormfix.platform.api.RequestTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = SignupController.class)
public class SignupExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException error,
            HttpServletRequest request) {
        List<ApiError.FieldError> fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new ApiError.FieldError(field.getField(), field.getDefaultMessage()))
                .sorted(Comparator.comparing(ApiError.FieldError::field))
                .toList();
        return response(request, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "The request contains invalid fields.", fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest(HttpServletRequest request) {
        return response(request, HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "The request body is invalid.", List.of());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> duplicateEmail(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS",
                "An account with this email already exists.", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> signupConflict(HttpServletRequest request) {
        return response(request, HttpStatus.CONFLICT, "SIGNUP_CONFLICT",
                "The account could not be created because the submitted identity already exists.", List.of());
    }

    private ResponseEntity<ApiError> response(HttpServletRequest request, HttpStatus status,
            String code, String message, List<ApiError.FieldError> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message,
                request.getRequestURI(),
                (String) request.getAttribute(RequestTraceFilter.TRACE_ATTRIBUTE), fields));
    }
}
