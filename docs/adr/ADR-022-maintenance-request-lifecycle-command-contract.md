# ADR-022: MaintenanceRequest lifecycle command 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-04

2026-11-04 사람 승인으로 이 계약을 제안대로 수락했다. `close`, `reject`, `mark-duplicate`가 같은
transaction에서 active assignment를 종료하고 `resolve`는 종료하지 않는다는 점을 포함한다.

## Context

Frozen V1 계약은 이전 어떤 슬라이스보다 이 슬라이스를 많이 정해두었다. Permission matrix는 모든
전이에 source, target, 인가 관계를 준다. `start`는 `ASSIGNED`를 `IN_PROGRESS`로, `hold`는
`IN_PROGRESS`를 `ON_HOLD`로, `resume`은 `ON_HOLD`를 다시 `IN_PROGRESS`로, `resolve`는
`IN_PROGRESS`를 `RESOLVED`로 옮기며 각각 current active worker가 수행한다. `close`와 `reopen`은
`RESOLVED`에 작용하고 reporter `RESIDENT` 또는 scope 안의 `ADMIN`/`SUPER_ADMIN`에게 속한다.
`reject`와 `mark duplicate`는 `REPORTED`에 작용하고 scope 안의 `ADMIN`/`SUPER_ADMIN`에게 속한다.
`CLOSED`, `REJECTED`, `DUPLICATE`는 terminal이고, `CLOSED` request는 reopen할 수 없으며,
`SUPER_ADMIN`도 그중 어떤 것도 우회하지 않는다. API inventory는 여덟 경로를 열거하고 전부 submitted
entity version을 요구한다고 밝히며, 낡은 제출은 `409 VERSION_CONFLICT`로 답한다. ERD는 이미
`status_reason`, `resolved_at`, `closed_at`, `duplicate_of_id`를 들고 있다.

Frozen 계약이 정하지 않은 것은 다음이다.

1. 각 command의 request DTO, 그리고 어떤 command가 reason을 받거나 요구하는지
2. 전이를 거치며 `status_reason`이 살아가는 방식. 이후 전이가 덮어쓰는지, 유지하는지, 지우는지
3. `resolved_at`과 `closed_at`을 언제 쓰는지, 그리고 `reopen`이 그것들에 무엇을 하는지
4. `duplicate_of_id`의 검증 규칙. ERD는 이것이 열려 있다고 분명히 적는다. duplicate가 original을
   참조한다는 것과 "no invented chain/cycle policy"만 기록한다
5. 여덟 command의 RequestHistory event type. V7 check 제약이 `REQUEST_CREATED`,
   `REQUEST_ASSIGNED`, `REQUEST_REASSIGNED`만 허용하므로 중요하다
6. frozen `403 WORKER_NOT_ASSIGNED` code가 `403 ACCESS_DENIED`에 대해 가지는 정확한 의미
7. terminal 전이가 active assignment를 종료하는지, 그리고 active assignment가 없을 때 `reopen`이
   request를 `IN_PROGRESS`로 옮길 수 있는지

## Decision

### 한 가지 command 형태와 두 예외

여섯 command가 `LifecycleCommandRequest(statusReason?, version)`를 받는다. `start`, `resume`,
`resolve`, `close`, `reopen`, `hold`다. 이 중 `hold`는 `statusReason`을 필수로 받고 나머지 다섯은
optional로 받는다. `reject`도 같은 형태를 받되 `statusReason`을 필수로 요구한다.
`mark-duplicate`는 `MarkDuplicateRequest(duplicateOfId, statusReason?, version)`를 받는다.

`hold`와 `reject`가 reason을 요구하는 이유는 각각 다음 독자가 복원할 수 없는 상태를 남기기
때문이다. 작업이 멈췄거나, 신고가 거부되었다. `statusReason`은 column에 맞춰 최대 500자다.

어떤 command도 위에 적힌 경우를 빼면 `status`, `assigneeId`, lifecycle timestamp, `reporterId`,
`priority`, `duplicateOfId`를 받지 않는다. Actor는 항상 JWT subject다.

### status_reason은 현재 status를 반영하고 audit은 RequestHistory가 담당한다

모든 전이가 `status_reason`을 쓴다. Command가 값을 담으면 그 값으로, 담지 않으면 `null`로 쓴다.
이것은 request가 현재 상태에 있는 이유의 투영이며 로그가 아니다.

Durable audit은 append-only RequestHistory 행이고, `status_reason`이 다음 값으로 넘어간 뒤에도 모든
전이의 reason을 보관한다. Column에 reason을 누적하면 그 audit을 변경 가능한 field에 중복으로 두게
되고, resolved된 request가 한때 hold되었던 이유를 계속 표시하게 된다.

### Lifecycle timestamp

`resolve`가 `resolved_at`을 쓴다. `close`가 `closed_at`을 쓴다. `reopen`은 request가 더 이상
resolved가 아니므로 `resolved_at`을 지우고, `CLOSED` request는 reopen할 수 없으므로 `closed_at`은
절대 건드리지 않는다. Reopen 후 다시 resolve하면 `resolved_at`을 덮어쓴다. `reject`와
`mark-duplicate`는 둘 다 쓰지 않는다. 어느 쪽도 해결이나 종료가 아니고, 각자의 history 행이 시각을
담는다.

### duplicate_of_id

`mark-duplicate`는 `duplicateOfId`를 요구한다. 참조된 original은 존재해야 하고 ADR-020 read 가시성
규칙 아래에서 actor에게 보여야 한다. 아니면 응답은 없는 request와 동일한 `404 REQUEST_NOT_FOUND`이며,
그래서 이 command는 actor가 읽을 수 없는 request를 노출하지 않는다.

Request는 자기 자신의 duplicate가 될 수 없고, original 자체가 `DUPLICATE`여서도 안 된다. 둘 다
`409 INVALID_REQUEST_STATE`다. 따라서 V1은 duplicate chain을 전혀 허용하지 않는다. 모든
`duplicate_of_id`는 duplicate가 아닌 request를 가리키므로 graph 순회 없이 cycle이 불가능해진다.
이것이 ERD가 열어둔 공백을 닫는 가장 좁은 규칙이다.

`duplicate_of_id`는 한 번만 쓴다. `DUPLICATE`가 terminal이므로 이후 어떤 command도 바꿀 수 없다.

### Terminal 전이는 active assignment를 종료하고 resolve는 종료하지 않는다

`close`, `reject`, `mark-duplicate`는 active assignment가 있으면 같은 transaction에서 종료하며
`unassigned_at`과 `unassigned_by_id`에 수행자를 기록한다. 닫히거나 거부되거나 중복인 request를
작업하는 사람은 없고, 행을 활성으로 남겨두면 그 request가 `assigned-to-me` worker listing에 영구히
남는다.

`resolve`는 assignment를 종료하지 않는다. Matrix는 `reopen`이 request를 `IN_PROGRESS`로 되돌리도록
요구하는데, 이는 그것을 resolve한 worker가 아직 배정되어 있을 때만 일관된다.

따라서 `reopen`은 active assignment를 요구한다. 없으면 응답은 `409 INVALID_REQUEST_STATE`이고,
administrator가 reopen 전에 worker를 배정한다. 아무도 배정되지 않은 상태로 request를 `IN_PROGRESS`로
옮기면 current active worker만 그것에 작용할 수 있다는 matrix 규칙과 모순된다.

### 인가와 두 개의 403 code

인가는 모든 state 검사보다 먼저 실행되므로, conflict 응답이 caller가 작용할 수 없는 request의 상태를
드러내지 않는다.

`start`, `hold`, `resume`, `resolve`는 caller가 current active assignee일 것을 요구한다. `WORKER`
role을 가졌지만 그 assignee가 아닌 caller, 즉 배정되지 않은 worker나 행이 종료된 former assignee는
`403 WORKER_NOT_ASSIGNED`를 받는다. 해당 command를 쓸 수 없는 role의 caller는 `403 ACCESS_DENIED`를
받는다. 이 구분은 frozen `WORKER_NOT_ASSIGNED` code에 정확한 의미를 주고 `ACCESS_DENIED`를 role
실패에 남긴다.

`close`와 `reopen`은 caller가 id가 `reporter_id`와 같은 `RESIDENT`이거나 scope 안의
`ADMIN`/`SUPER_ADMIN`일 때 허용한다. `reject`와 `mark-duplicate`는 scope 안의
`ADMIN`/`SUPER_ADMIN`만 허용한다. Scope 해석은 `DormitoryScopeResolutionService`를 재사용한다.

### Version, state, 원자성

Submitted `version`은 command 실행 전에 비교한다. 불일치는 `409 VERSION_CONFLICT`이고 flush 시점의
optimistic-locking 실패도 같다. Matrix 밖의 source state인 command는
`409 INVALID_REQUEST_STATE`다. Command는 멱등이 아니므로 이미 완료된 전이를 반복하면 조용히
성공하지 않고 conflict가 된다.

Command당 transaction 하나가 state 변경, 해당되는 경우의 terminal assignment 종료, timestamp와
reason 기록, 그리고 append-only RequestHistory 정확히 한 행을 수행한다.

### Audit 행과 V8

여덟 command는 `REQUEST_STARTED`, `REQUEST_HELD`, `REQUEST_RESUMED`, `REQUEST_RESOLVED`,
`REQUEST_CLOSED`, `REQUEST_REOPENED`, `REQUEST_REJECTED`, `REQUEST_MARKED_DUPLICATE`를 append한다.
각 행은 actor, 이전 status, 새 status, 제공된 reason을 기록한다. `REQUEST_MARKED_DUPLICATE`는
`metadata`에 `{"duplicateOfId": <id>}`도 담는다.

V8이 `ck_request_history_event_type`을 교체해 기존 셋에 이 여덟을 더한다. V6와 V7은 수정하지 않고,
V7이 이미 그랬듯 제약을 drop한 뒤 재생성한다.

전이는 `MaintenanceRequest`의 named command이며 각자 자신의 source state를 검증한다. Public status
setter도 status PATCH도 없다.

## Contract synchronization

승인되면 여덟 command DTO, reason 요구사항, `status_reason`과 timestamp 정책, `duplicate_of_id`
규칙, terminal-assignment 규칙, `reopen` 전제조건, `WORKER_NOT_ASSIGNED` 대 `ACCESS_DENIED` 구분,
여덟 event type을 Frozen API specification과 permission matrix에 동기화하고, V8 제약 변경을 ERD
note에 기록하며, frozen baseline hash를 갱신한다. 구현은 같은 요청 슬라이스에서 이어진다.

## Alternatives

### 1. `status_reason`에 reason을 누적한다

Column에 모든 reason을 담으면 history를 읽지 않고도 최신 reason을 볼 수 있다. 거부한 이유는 column이
500자로 제한되고, append-only RequestHistory가 이미 전체 순서를 보관하며, resolved된 request가 한때
hold되었던 이유를 계속 표시하게 되기 때문이다.

### 2. close, reject, mark-duplicate에서 active assignment를 그대로 둔다

Lifecycle command가 assignment 행을 건드리지 않으므로 관심사마다 writer가 하나로 유지된다. 거부한
이유는 terminal request가 active assignment를 영구히 유지하고 모든 `assigned-to-me` listing에 남게
되는데, read 계약에 이를 걸러낼 방법이 없기 때문이다.

### 3. active assignment 없이 `reopen`이 request를 `IN_PROGRESS`로 옮기게 한다

Worker가 배정 해제된 administrator에게 두 번째 단계를 덜어준다. 거부한 이유는 아무도 배정되지 않은
`IN_PROGRESS`가 current active worker만 작용할 수 있다는 matrix 규칙과 모순되고, 그 request에서
`start`, `hold`, `resume`을 쓸 수 없게 되기 때문이다.

### 4. duplicate chain을 허용하고 최종 original로 해석한다

Administrator가 B를 A의 duplicate로 표시한 뒤 A를 Z의 duplicate로 표시하는 것을 허용한다. 그러면
cycle 검사와, 읽을 때마다 해석 순회가 필요하다. `DUPLICATE` original을 거부하면 불변식이 비교 한
번으로 끝나고, administrator는 여전히 B를 Z로 직접 가리킬 수 있다.

### 5. 보이지 않는 request의 `duplicateOfId`를 `403`으로 한다

"당신 것이 아님"과 "없음"을 구분한다. ADR-020이 read에서 `404`를 택한 것과 같은 이유로 거부한다.
Request ID가 순번이므로 여기서 `403`을 주면 administrator가 이 command로 다른 dormitory를 탐색할 수
있다.

### 6. 모든 command에 `statusReason`을 요구한다

Payload 형태가 균일해 문서화가 단순하다. 거부한 이유는 `start`, `resume`, 그리고 resolved request의
`close`는 설명이 필요하지 않고, 강제하면 audit 품질을 떨어뜨리는 형식적인 문구가 생기기 때문이다.

## Consequences

이 슬라이스는 request aggregate에 대해 REPORT에서 CONFIRM까지의 workflow를 완성하고,
`WORKER_NOT_ASSIGNED`와 version을 검사하는 command protocol을 여덟 command에 걸쳐 실제로 지탱하게
만든다. `status_reason`, `resolved_at`, `closed_at`에 대한 첫 lifecycle 기록과, lifecycle command가
assignment 행을 종료하는 첫 사례를 도입한다.

Terminal 전이가 active assignment를 종료하므로 worker listing에 terminal 작업이 나타나지 않지만,
닫힌 request의 assignment 이력은 이제 항상 재배정한 administrator가 아니라 닫은 administrator가
`unassigned_by_id`로 끝난다.

`DUPLICATE` original을 거부한다는 것은 최종 original을 가리키려는 client가 chain을 직접 읽어야 한다는
뜻이지만, V1은 읽을 chain이 없음을 보장한다.

Visit, work log, attachment, comment는 범위 밖이고 request metadata PATCH도 그렇다.

구현 후 기록한 관찰: 세 terminal 전이 중 실제로 종료할 assignment를 가질 수 있는 것은 `close`뿐이다.
`reject`와 `mark-duplicate`는 `REPORTED`에서 떠나고, 배정이 같은 transaction에서 request를
`ASSIGNED`로 옮기므로 `REPORTED` request는 배정된 적이 없다. 따라서 오늘 이 둘에 대해 규칙은 공문구
다. 그래도 둘 모두에 규칙을 유지하고 강제한다. 불변식이 그 우연에 의존하지 않게 하고, 나중에 어느
한쪽의 source state를 넓히는 슬라이스가 공백 대신 올바른 동작을 물려받게 하기 위해서다.
