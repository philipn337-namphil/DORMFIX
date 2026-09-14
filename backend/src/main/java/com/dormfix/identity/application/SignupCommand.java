package com.dormfix.identity.application;

public record SignupCommand(String name, String email, String password,
        String phone, String studentNumber) {
}
