package com.dormfix.identity.application;

import org.springframework.security.access.AccessDeniedException;

public class DormitoryScopeAccessDeniedException extends AccessDeniedException {
    public DormitoryScopeAccessDeniedException() {
        super("Access is denied.");
    }
}
