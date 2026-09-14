package com.dormfix.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password) {
    public LoginRequest {
        if (email != null) {
            email = email.strip();
        }
    }
}
