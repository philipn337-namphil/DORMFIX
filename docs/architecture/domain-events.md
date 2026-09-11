# Frozen V1 domain event

RequestHistory는 aggregate 변경과 함께 commit되는 durable append-only audit이고, domain event는 완료된 business action을 다른 component에 알리는 value notification이다. Read나 사소한 metadata edit에는 event를 만들지 않는다.

| Group | Frozen event names |
|---|---|
| Request | MaintenanceRequestCreated, MaintenanceRequestAssigned, MaintenanceRequestReassigned, MaintenanceStarted, MaintenanceHeld, MaintenanceResumed, MaintenanceResolved, MaintenanceClosed, MaintenanceReopened, MaintenanceRejected, MaintenanceMarkedDuplicate |
| Visit | MaintenanceVisitScheduled, MaintenanceVisitRescheduled, MaintenanceVisitCompleted, MaintenanceVisitCanceled, MaintenanceVisitNoAccess |
| Communication | CommentCreated, AttachmentAdded |

Event payload는 immutable ID/value data만 가지며 managed JPA entity/lazy graph를 넣지 않는다. Domain entity는 `ApplicationEventPublisher`에 의존하지 않는다. Application Service가 transaction 중 Spring event를 publish하고 side-effect consumer는 `@TransactionalEventListener(phase = AFTER_COMMIT)`에서 동작한다. Notification DB write는 별도 `REQUIRES_NEW` transaction으로 수행한다.

V1 in-process event에는 crash window가 있으며 exactly-once를 주장하지 않는다. RequestHistory가 authoritative audit이다. V1에는 outbox table/queue를 만들지 않는다. V2 확장 지점은 application value event → transactional outbox → SQS → idempotent worker/retry/DLQ다.
