package com.dormfix.maintenance.application;

/**
 * Raised when a caller holding the WORKER role is not the current active assignee, including a
 * former assignee whose row has ended. Answers 403 WORKER_NOT_ASSIGNED, which is distinct from
 * ACCESS_DENIED: that one is for a role that cannot use the command at all.
 */
public class WorkerNotAssignedException extends RuntimeException {
    public WorkerNotAssignedException() {
        super("The caller is not the current assignee.");
    }
}
