package com.dormfix.identity.api;

import com.dormfix.identity.application.SignupCommand;
import com.dormfix.identity.application.SignupResult;
import com.dormfix.identity.application.SignupService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class SignupController {
    private final SignupService signupService;

    public SignupController(SignupService signupService) {
        this.signupService = signupService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        SignupResult result = signupService.signup(new SignupCommand(
                request.name(), request.email(), request.password(), request.phone(), request.studentNumber()));
        return new SignupResponse(result.id(), result.email(), result.name(), result.phone(),
                result.studentNumber(), result.status(), result.roles(), result.createdAt());
    }
}
