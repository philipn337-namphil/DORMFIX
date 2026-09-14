package com.dormfix.identity.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank String password,
        @Size(max = 20) String phone,
        @Size(max = 30) String studentNumber) {
    public SignupRequest {
        if (email != null) {
            email = email.strip();
        }
    }
}
