# ADR-029: Notification과 V1 domain event 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-27

2026-11-27 사람 승인으로 이 계약을 제안대로 수락했다. best-effort·최대 한 번 전달, event별 수신자 표,
서버 고정 문구, 본인 notification만 다루는 네 경로, V14 schema를 포함한다.

## Context

Frozen 범위에는 notification 경로 넷(`GET /notifications`, `PATCH /notifications/{id}/read`,
`POST /notifications/read-all`, `GET /notifications/unread-count`)과 Notification table(ERD), 그리고
domain event 이름 목록(`domain-events.md`)이 있다. 하지만 다음이 비어 있다.

1. Notification `type` enum. ERD가 "Not specified; review before DDL"로 남겼다.
2. 어떤 event가 누구에게 notification을 만드는지
3. `title`과 `message`에 무엇을 쓰는지, 무엇을 쓰면 안 되는지
4. 네 경로의 DTO, 필터, 정렬, 페이징, 오류
5. 전달 허용 수준. [ADR-009](ADR-009-events-v1-v2.md)는 "listener 실패가 command를 rollback하지 못하므로
   전달 허용 수준은 notification 출시 전에 승인되어야 한다"고 적었다.

현재 코드는 domain event를 하나도 발행하지 않는다. `notification` package는 비어 있다.

Frozen이 이미 정한 것은 그대로 따른다. Event는 ID와 value만 담고 managed entity를 담지 않는다.
Application service가 command transaction 안에서 Spring event를 publish하고, consumer는
`@TransactionalEventListener(phase = AFTER_COMMIT)`에서 동작하며, notification DB write는 별도
`REQUIRES_NEW` transaction이다. V1에는 outbox/SQS가 없고 exactly-once를 주장하지 않는다.

## Decision

### 전달 허용 수준: best-effort, 최대 한 번

Notification은 편의 기능이고 authoritative 기록은 RequestHistory다. 다음을 V1 허용 수준으로 승인한다.

- Command commit 뒤 process가 죽거나 listener가 실패하면 그 notification은 유실될 수 있다. 재시도하지
  않는다. 같은 event로 notification을 두 번 만들지는 않는다.
- Listener 예외는 잡아서 event 이름과 `requestId`만 WARN으로 남긴다. Notification 본문, 사유, comment
  내용은 로그에 남기지 않는다. Command 응답은 이미 성공으로 나갔으므로 바뀌지 않는다.
- Command가 rollback되면 AFTER_COMMIT listener가 실행되지 않으므로 notification도 없다.

### 발행할 event와 받는 사람

Consumer가 있는 event만 이번 슬라이스에서 발행한다. Frozen 목록의 `AttachmentAdded`는 notification을
만들지 않으므로 consumer가 생길 때 추가한다(consumer 없는 event는 추측성 코드다).

공통 규칙:
- **행동한 사람(actor) 자신에게는 보내지 않는다.**
- 한 event에서 같은 사람이 두 역할로 걸리면 notification은 하나만 만든다.
- `SUPER_ADMIN`은 수신 대상으로 고르지 않는다(전역 fan-out 방지). 다른 관계로 걸리면 받는다.
- 받는 사람의 계정 status는 거르지 않는다. 기록만 하고 조회는 본인 인증이 필요하다.

| Event | 받는 사람 | `type` |
|---|---|---|
| MaintenanceRequestCreated | 그 request 기숙사를 scope로 가진 `ADMIN` 전원 | `REQUEST_SUBMITTED` |
| MaintenanceRequestAssigned | 배정된 worker | `ASSIGNED_TO_YOU` |
| | reporter | `REQUEST_ASSIGNED` |
| MaintenanceRequestReassigned | 새 worker | `ASSIGNED_TO_YOU` |
| | 이전 worker | `UNASSIGNED_FROM_YOU` |
| | reporter | `REQUEST_ASSIGNED` |
| MaintenanceStarted | reporter | `REQUEST_STARTED` |
| MaintenanceHeld | reporter | `REQUEST_HELD` |
| MaintenanceResumed | reporter | `REQUEST_RESUMED` |
| MaintenanceResolved | reporter (확인 요청) | `REQUEST_RESOLVED` |
| MaintenanceClosed | close로 종료된 assignment의 worker, reporter | `REQUEST_CLOSED` |
| MaintenanceReopened | active assignee | `REQUEST_REOPENED` |
| MaintenanceRejected | reporter | `REQUEST_REJECTED` |
| MaintenanceMarkedDuplicate | reporter | `REQUEST_MARKED_DUPLICATE` |
| MaintenanceVisitScheduled | reporter, visit worker | `VISIT_SCHEDULED` |
| MaintenanceVisitRescheduled | reporter, visit worker | `VISIT_RESCHEDULED` |
| MaintenanceVisitCanceled | reporter, visit worker | `VISIT_CANCELED` |
| MaintenanceVisitCompleted | reporter | `VISIT_COMPLETED` |
| MaintenanceVisitNoAccess | reporter | `VISIT_NO_ACCESS` |
| CommentCreated (`PUBLIC`) | reporter, active assignee | `COMMENT_ADDED` |
| CommentCreated (`STAFF_ONLY`) | active assignee | `COMMENT_ADDED` |

`STAFF_ONLY` comment는 reporter에게 어떤 notification도 만들지 않는다. Resident가 internal remark의
존재를 notification으로 알 수 없게 하기 위해서다(ADR-026과 일관).

Event payload는 listener가 받는 사람을 고르는 데 필요한 ID를 command transaction 안에서 채워 담는다:
`requestId`, `requestNumber`, `actorId`, `reporterId`, `dormitoryId`(Created만), `workerId`/
`previousWorkerId`(배정), `visitId`와 visit `workerId`(visit), comment `visibility`와 그 시점의 active
assignee id(comment). Listener는 Created의 admin 목록 조회 외에는 maintenance 데이터를 다시 읽지 않는다.

### 문구: 서버 고정 문구, 자유 텍스트 금지

`title`(≤150)과 `message`(≤500)는 `type`별 한국어 고정 문구에 `requestNumber`만 끼워 서버가 만든다.
예: `title = "담당자가 배정되었습니다"`, `message = "DF-42 신고에 담당자가 배정되었습니다."`

사용자가 입력한 텍스트는 넣지 않는다. 신고 제목·설명, lifecycle 사유, comment 내용, visit note, 출입
동의 정보가 모두 해당한다. Notification은 수신자가 그 request에 대한 관계를 잃은 뒤에도 남기 때문에
(former worker 등), 남아도 되는 정보만 담는다. 자세한 내용은 client가 `requestId`로 해당 API를 읽는다.

### API

모든 경로는 인증된 caller 본인의 notification만 다룬다. Role 제한은 없다.

- `GET /notifications?unread=&page=&size=` → `NotificationPageResponse(content, page, size,
  totalElements, totalPages)`. `unread=true`면 `read_at IS NULL`만. 정렬 `createdAt DESC, id DESC`
  고정. `page` 0부터, `size` `1..100` 기본 `20`, 위반은 `400 VALIDATION_FAILED`.
- Item DTO: `NotificationResponse(id, type, requestId, title, message, read, readAt, createdAt)`.
  `recipientId`는 응답하지 않는다(항상 본인).
- `PATCH /notifications/{id}/read` → 본문 없음, `NotificationResponse` 반환. 이미 읽은 것을 다시
  읽음 처리해도 성공이며 `readAt`은 처음 값을 유지한다(멱등). 남의 notification이나 없는 id는 둘 다
  `404 NOTIFICATION_NOT_FOUND`(새 code)다. ID가 순번이므로 403은 존재 oracle이 된다.
- `POST /notifications/read-all` → 본인의 안 읽은 notification을 모두 읽음 처리, `204 No Content`.
- `GET /notifications/unread-count` → `UnreadCountResponse(count)`.
- Notification을 만들거나 지우는 API는 없다.

### Schema (V14)

ERD column 그대로 `notification` table을 만든다.
- `type` VARCHAR(50) NOT NULL, 위 type 목록의 CHECK. `title` VARCHAR(150) NOT NULL, `message`
  VARCHAR(500) NOT NULL, `read_at` TIMESTAMPTZ NULL, `created_at` TIMESTAMPTZ NOT NULL DEFAULT now.
- `recipient_id` FK → app_user, `request_id` FK → maintenance_request NULL, 둘 다 ON DELETE RESTRICT.
- Index: `(recipient_id, created_at DESC, id DESC)` 목록용, `(recipient_id) WHERE read_at IS NULL`
  partial index는 unread 개수와 read-all용.
- `read_at`만 insert 뒤에 쓰인다. V1에는 삭제 경로와 보존 기한이 없다(별도 정책 필요 시 ADR).

### 구조

- Event record는 `maintenance.application.event`에 둔다. Domain은 event를 모른다.
- `notification` feature가 listener, `NotificationRecorder`(REQUIRES_NEW), 조회 service, controller를
  갖는다. 의존 방향은 notification → maintenance(event 타입), notification → identity(admin scope
  조회)이며 maintenance는 notification을 모른다. ArchUnit의 slice cycle 규칙으로 고정된다.

## Alternatives

1. **Frozen event 18개를 모두 지금 발행한다.** 목록과 맞지만 consumer 없는 event는 실행되지 않는 코드다.
   필요할 때 추가하는 비용이 작다.
2. **Created를 `SUPER_ADMIN`에게도 보낸다.** 놓치는 신고가 줄지만 모든 기숙사의 신고가 전역 관리자에게
   몰린다. Scope 없는 기숙사가 생기면 그때 재검토한다.
3. **문구에 신고 제목이나 사유를 넣는다.** 알림만 보고도 내용을 알 수 있지만, 관계를 잃은 사람에게도
   자유 텍스트가 남는다. 고정 문구 + `requestId`로 대신한다.
4. **Listener 실패 시 재시도한다.** 신뢰성은 오르지만 in-process 재시도는 crash 구간을 없애지 못하고,
   그 문제는 V2 outbox가 다룬다. V1은 best-effort로 둔다.
5. **Visit/comment notification 없이 상태 전이만 보낸다.** 범위는 작아지지만 resident가 가장 알아야 할
   방문 일정이 빠진다.

## Consequences

- 승인되면 Frozen API spec(경로 DTO, 오류), permission matrix(notification 행), ERD(type enum, V14
  규칙), error contract(`NOTIFICATION_NOT_FOUND`)를 동기화하고 baseline hash를 갱신한다.
  `domain-events.md`는 바꾸지 않는다(AttachmentAdded 미발행은 이름 목록과 충돌하지 않는다).
- 각 command service가 commit 전에 event를 publish하도록 바뀐다. Event 발행 자체는 command 결과에
  영향을 주지 않는다.
- 통합 테스트는 event별 수신자, actor 제외, STAFF_ONLY 은닉, rollback 시 미생성, listener 실패가
  command를 깨지 않음, 본인 외 접근 404를 검증한다.
- 유실 가능성이 있으므로 client는 notification을 진실 원천으로 쓰지 않는다.
