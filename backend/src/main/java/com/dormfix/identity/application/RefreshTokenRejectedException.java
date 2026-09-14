package com.dormfix.identity.application;

public class RefreshTokenRejectedException extends RuntimeException {
    public RefreshTokenRejectedException() {
        super("The refresh token is invalid.");
    }
}
