package com.dormfix.maintenance.application;

/**
 * Raised when an active assignment already blocks the command: the named worker is already the
 * active assignee, or a concurrent command lost the active-assignment unique index race. Answers
 * 409 REQUEST_ALREADY_ASSIGNED and is deliberately not folded into VERSION_CONFLICT.
 */
public class RequestAlreadyAssignedException extends RuntimeException {
    public RequestAlreadyAssignedException() {
        super("The request already has this active assignment.");
    }
}
