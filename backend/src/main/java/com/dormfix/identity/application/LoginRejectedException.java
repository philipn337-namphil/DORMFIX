package com.dormfix.identity.application;

public class LoginRejectedException extends RuntimeException {
    public LoginRejectedException() {
        super("Login credentials or account status rejected the request.");
    }
}
