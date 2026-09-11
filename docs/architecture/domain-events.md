# Frozen V1 domain events

RequestHistory is durable append-only audit data, committed with the aggregate change. A domain event announces a completed business action for other components. Neither substitutes for the other. Reads and every minor metadata edit do not need events.

| Group | Frozen event names |
|---|---|
| Request | MaintenanceRequestCreated, MaintenanceRequestAssigned, MaintenanceRequestReassigned, MaintenanceStarted, MaintenanceHeld, MaintenanceResumed, MaintenanceResolved, MaintenanceClosed, MaintenanceReopened, MaintenanceRejected, MaintenanceMarkedDuplicate |
| Visit | MaintenanceVisitScheduled, MaintenanceVisitRescheduled, MaintenanceVisitCompleted, MaintenanceVisitCanceled, MaintenanceVisitNoAccess |
| Communication | CommentCreated, AttachmentAdded |

Use immutable IDs/value data, never managed JPA entities or lazy graphs. Domain entities do not depend on ApplicationEventPublisher. Application services may publish Spring events inside the command transaction; consumers that cause side effects use @TransactionalEventListener(phase = AFTER_COMMIT). Publishing within the transaction plus AFTER_COMMIT handling is how rollback prevents effects. Do not publish only after a transaction and then expect a transaction-bound listener to find it.

An AFTER_COMMIT listener that persists Notification delegates to a separate Spring bean's @Transactional(propagation = REQUIRES_NEW) method. It must not try to write into the completed command transaction. Test rollback produces no notification and committed events use a new transaction. Record handler failure with event/request IDs and trace ID without sensitive payload; isolate failure so callers are not told the business command rolled back when it committed. No retry by blindly repeating the business command.

V1 in-process delivery has a crash window and is not durable/exactly-once. Document and review the accepted notification-loss budget before releasing notifications. RequestHistory remains authoritative even when notification delivery fails. Avoid claims of guaranteed eventual delivery without an outbox.

V2 integration point: application value events -> transactionally persisted outbox -> SQS -> idempotent notification worker with retry/dead-letter queue. Do not create the outbox table or queue now. ADR-009 records this deferred evolution. Spring's [transaction-bound event documentation](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html) explains phase semantics.
