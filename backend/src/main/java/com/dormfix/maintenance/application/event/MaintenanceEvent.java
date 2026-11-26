package com.dormfix.maintenance.application.event;

/**
 * V1 in-process domain events (frozen names, ADR-009, ADR-029). Each carries IDs and values only,
 * never a managed entity, and is published by an application service inside the command
 * transaction; consumers run AFTER_COMMIT. Every event carries what a consumer needs to choose its
 * recipients, so the consumer does not re-read maintenance data. RequestHistory, not this, is the
 * authoritative audit.
 *
 * <p>AttachmentAdded is a frozen name but has no consumer yet, so it is not published.
 */
public sealed interface MaintenanceEvent {
    Long requestId();

    String requestNumber();

    Long actorId();

    Long reporterId();

    /** Builds the events whose payload is only the common fields. */
    @FunctionalInterface
    interface Factory {
        MaintenanceEvent create(Long requestId, String requestNumber, Long actorId,
                Long reporterId);
    }

    record MaintenanceRequestCreated(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long dormitoryId) implements MaintenanceEvent { }

    record MaintenanceRequestAssigned(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long workerId) implements MaintenanceEvent { }

    record MaintenanceRequestReassigned(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long workerId, Long previousWorkerId) implements MaintenanceEvent { }

    record MaintenanceStarted(Long requestId, String requestNumber, Long actorId,
            Long reporterId) implements MaintenanceEvent { }

    record MaintenanceHeld(Long requestId, String requestNumber, Long actorId,
            Long reporterId) implements MaintenanceEvent { }

    record MaintenanceResumed(Long requestId, String requestNumber, Long actorId,
            Long reporterId) implements MaintenanceEvent { }

    record MaintenanceResolved(Long requestId, String requestNumber, Long actorId,
            Long reporterId) implements MaintenanceEvent { }

    /** {@code endedWorkerId} is the worker whose assignment the close ended, or null. */
    record MaintenanceClosed(Long requestId, String requestNumber, Long actorId, Long reporterId,
            Long endedWorkerId) implements MaintenanceEvent { }

    record MaintenanceReopened(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long workerId) implements MaintenanceEvent { }

    record MaintenanceRejected(Long requestId, String requestNumber, Long actorId,
            Long reporterId) implements MaintenanceEvent { }

    record MaintenanceMarkedDuplicate(Long requestId, String requestNumber, Long actorId,
            Long reporterId) implements MaintenanceEvent { }

    record MaintenanceVisitScheduled(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long visitId, Long workerId) implements MaintenanceEvent { }

    record MaintenanceVisitRescheduled(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long visitId, Long workerId) implements MaintenanceEvent { }

    record MaintenanceVisitCanceled(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long visitId, Long workerId) implements MaintenanceEvent { }

    record MaintenanceVisitCompleted(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long visitId, Long workerId) implements MaintenanceEvent { }

    record MaintenanceVisitNoAccess(Long requestId, String requestNumber, Long actorId,
            Long reporterId, Long visitId, Long workerId) implements MaintenanceEvent { }

    /**
     * {@code staffOnly} decides whether the reporter may hear of it at all; {@code activeAssigneeId}
     * is the assignee at the moment the comment was written, or null.
     */
    record CommentCreated(Long requestId, String requestNumber, Long actorId, Long reporterId,
            Long commentId, boolean staffOnly, Long activeAssigneeId) implements MaintenanceEvent { }
}
