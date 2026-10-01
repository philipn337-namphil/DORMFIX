package com.dormfix.identity.application;
public class UserNotFoundException extends RuntimeException { public UserNotFoundException(Long id) { super("User was not found: " + id); } }
