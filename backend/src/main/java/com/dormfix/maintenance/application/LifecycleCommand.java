package com.dormfix.maintenance.application;

/** Payload shared by every lifecycle transition. {@code duplicateOfId} is only used by mark-duplicate. */
public record LifecycleCommand(Long requestId, String statusReason, Long duplicateOfId,
        Long version) { }
