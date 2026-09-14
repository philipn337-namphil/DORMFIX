package com.dormfix.identity.api;

import com.dormfix.identity.application.LoginCommand;
import com.dormfix.identity.application.LoginResult;
import com.dormfix.identity.application.LoginService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {
    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = loginService.login(new LoginCommand(request.email(), request.password()));
        return new LoginResponse(result.accessToken(), result.tokenType(), result.expiresAt(),
                result.refreshToken(), result.refreshTokenExpiresAt());
    }
}
