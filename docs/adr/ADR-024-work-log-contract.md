# ADR-024: WorkLog 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-08

2026-11-08 사람 승인으로 이 계약을 제안대로 수락했다. 읽기를 staff 전용으로 두는 것과 `ADMIN`이
WorkLog를 작성하지 않는 것을 포함한다.

## Context

Frozen V1은 WorkLog의 뼈대를 준다. ERD는 column을 `id`, `request_id`, nullable
`visit_id`, `worker_id`, `action_type`, `description`, `created_at`으로 정하고, `WorkActionType`을
`INSPECTION`, `REPAIR`, `REPLACEMENT`, `TEST`, `NOTE`로 열거한다. WorkLog는 정상 운영에서
append-only이며 임의 hard-delete하지 않는다. API inventory는 경로 둘을 둔다.
`POST /maintenance-requests/{id}/work-logs`(Append WorkLog)와
`GET /maintenance-requests/{id}/work-logs`(Read permitted WorkLogs)다. Permission matrix는
WorkLog를 `WORKER` 항목에만 적는다. current active assignee가 작성하고 former worker는
historical read-only다.

Frozen 계약이 정하지 않은 것은 다음이다.

1. create DTO
2. "permitted WorkLogs"가 누구를 뜻하는지. WorkLog에는 Comment의 `CommentVisibility`에 해당하는
   가시성 column이 없으므로 row별 필터가 아니라 role별 필터다. `RESIDENT`가 읽는지가 열려 있다
3. `ADMIN`이 WorkLog를 작성할 수 있는지. matrix는 `WORKER` 항목에만 적는다
4. `visit_id`의 검증 규칙. 같은 request의 visit이어야 하는지, 어떤 status까지 허용하는지
5. WorkLog 추가가 가능한 request 상태
6. `description`의 길이 상한. ERD는 `TEXT`만 말한다
7. 목록의 정렬과 페이징
8. WorkLog 추가가 RequestHistory 행을 남기는지. ERD의 event 예시에 work-log 항목이 없다

## Decision

### 작성

`POST /maintenance-requests/{id}/work-logs`는 `AppendWorkLogRequest(actionType, description,
visitId?)`를 받는다. `workerId`, `createdAt`, `id`는 받지 않는다. `worker_id`는 JWT subject다.

`actionType`은 frozen `WorkActionType` 다섯 값 중 하나이며, 해석할 수 없는 값은
`400 VALIDATION_FAILED`다. `description`은 필수이고 최대 2000자다. ERD가 물리 타입으로 `TEXT`를
주지만 DTO에 상한이 없으면 입력이 무제한으로 저장되므로, 신규 코드는 모든 문자열 DTO field에 상한을
두는 저장소 규칙을 따른다.

`visitId`를 제공하면 그 visit은 같은 request에 속해야 한다. 아니면 `409 INVALID_REQUEST_STATE`이고,
없는 visit은 `404 VISIT_NOT_FOUND`다. Visit의 status는 제한하지 않는다. 작업 기록은 완료된 visit과
진입 실패한 visit을 똑같이 서술할 수 있어야 하고, `CANCELED` visit에 대해 "가서 보니 필요 없었다"를
남기는 것도 정당한 기록이다.

### 작성자는 current active assignee뿐이다

작성 권한은 request의 current active assignee에게만 있다. `WORKER` role을 가졌지만 그 assignee가
아닌 caller는 `403 WORKER_NOT_ASSIGNED`, 해당 command를 쓸 수 없는 role은 `403 ACCESS_DENIED`다.

`ADMIN`과 `SUPER_ADMIN`은 WorkLog를 작성하지 않는다. matrix가 WorkLog를 `WORKER` 항목에만 적었고,
WorkLog는 "누가 무슨 작업을 했다"는 1인칭 기록이기 때문이다. Administrator가 남길 말은 comment에
속한다.

request는 terminal(`CLOSED`, `REJECTED`, `DUPLICATE`)이 아니어야 하며 아니면
`409 INVALID_REQUEST_STATE`다. 실무상 이 검사에 도달하는 경로는 없다. ADR-022에 따라 terminal 전이가
active assignment를 종료하므로 작성 권한이 먼저 `403`으로 끊긴다. 그래도 규칙을 명시하고 강제한다.
불변식이 그 우연에 의존하지 않게 하고, 나중에 작성 권한을 넓히는 슬라이스가 공백 대신 올바른
동작을 물려받게 하기 위해서다. ADR-022가 terminal 전이의 assignment 종료 규칙을 세 전이 모두에
유지한 것과 같은 이유다.

### 읽기는 staff 전용이다

`GET /maintenance-requests/{id}/work-logs`는 request의 assignment row를 가진 `WORKER`(현재든
종료든), scope 안의 `ADMIN`, `SUPER_ADMIN`이 읽는다. `RESIDENT`는 `403 ACCESS_DENIED`다.

WorkLog는 내부 작업 기록이다. Resident를 향한 소통 창구는 `PUBLIC` comment이고, resident가
`STAFF_ONLY` comment를 받지 않는 것과 같은 선을 긋는다. Frozen 문구가 "Read permitted WorkLogs"인
것은 필터가 존재한다는 뜻이고, WorkLog에 가시성 column이 없으므로 그 필터는 role 수준이다.

Former worker의 읽기 권한은 유지한다. matrix가 former worker에게 historical read-only를 주며,
WorkLog는 그가 직접 작성한 기록이다.

request가 caller의 가시성 밖이면 ADR-020의 read 규칙대로 `404 REQUEST_NOT_FOUND`다. 단
`RESIDENT`는 자기 request를 볼 수 있으므로 `403`을 받는다. 자기 request의 존재는 이미 알고 있어서
`403`이 새로 드러내는 것이 없다.

### 목록은 시간순이고 페이징한다

목록은 `createdAt ASC, id ASC`로 정렬한다. WorkLog는 작업의 서술이므로 오래된 것부터 읽어야 이야기가
된다. Visit 목록이 `scheduledStart DESC`인 것과 다르다. Visit은 "다음 일정이 무엇인가"를 묻고 WorkLog는
"무슨 일이 있었나"를 묻는다.

페이징은 `page`가 0부터 시작하고 기본값 `0`, `size`는 `1..100`에 기본값 `20`이다. 위반은
`400 VALIDATION_FAILED`다. Page envelope는 기존 형태를 재사용한다.

ADR-023이 Visit 목록을 페이징하지 않은 것과 다르다. Visit은 열린 visit 하나 규칙이 개수를
실질적으로 제한하는데, WorkLog에는 그런 제한이 없고 긴 수리는 기록이 계속 쌓인다. 저장소의 database
관례가 bounded pagination을 요구하므로 WorkLog는 페이징한다. Visit 목록도 누적될 수 있다는 점은
남는 약점이며, 필요해지면 별도 슬라이스에서 같은 경계를 추가한다.

### RequestHistory 행을 남기지 않는다

WorkLog 추가는 RequestHistory에 아무것도 append하지 않는다. WorkLog 자체가 durable append-only
기록이고, RequestHistory는 request의 lifecycle과 status 사건을 추적한다. ERD의 event 예시에도
work-log 항목이 없다. 같은 사실을 두 append-only table에 적으면 audit이 중복된다.

따라서 V10은 `ck_request_history_event_type`을 건드리지 않는다. V7, V8, V9가 연속으로 교체해 온
제약이 이 슬라이스에서는 그대로 남는다.

WorkLog 추가는 request를 전혀 수정하지 않는다. status도, `updated_at`도, `version`도 바꾸지 않으며
version을 검사하지도 않는다. 새 행을 추가할 뿐이므로 분실 갱신이 있을 수 없다.

### V10 migration

V10은 ERD가 정한 대로 `work_log`를 만든다. `action_type`은 CHECK 제약을 가진 string enum,
`description`은 NOT NULL `TEXT`, `created_at`은 `NOT NULL DEFAULT CURRENT_TIMESTAMP`다. request와
worker foreign key는 `ON DELETE RESTRICT`이고 nullable `visit_id`도 같다. 목록을 위해
`(request_id, created_at)` index를 둔다.

Append-only는 코드에서도 고정한다. 모든 column을 `updatable = false`로 매핑하고 mutator를 두지
않으며, delete나 update 경로를 제공하지 않는다.

## Contract synchronization

승인되면 create DTO, 작성자 제한, staff 전용 읽기와 former worker 유지, `visitId` 검증, terminal
request 규칙, `description` 상한, 목록 정렬과 페이징 경계, RequestHistory를 남기지 않는다는 결정을
Frozen API specification과 permission matrix에 동기화한다. V10 물리 규칙은 ERD note에 기록하고
frozen baseline hash를 갱신한다. 구현은 같은 요청 슬라이스에서 이어진다.

## Alternatives

### 1. `RESIDENT`에게 WorkLog 읽기를 허용한다

자기 방에 무슨 작업이 있었는지 투명하게 볼 수 있어 실제로 가치가 있다. 거부한 이유는 WorkLog가
`INSPECTION`/`TEST` 같은 내부 진단과 자유 서술을 담는 작업자용 기록이고, resident를 향한 창구는
`PUBLIC` comment로 이미 frozen에 있기 때문이다. Resident가 `STAFF_ONLY` comment를 받지 않는 것과
같은 선이다. 나중에 resident를 향한 수리 요약이 필요하면 별도 projection으로 추가할 수 있다.

### 2. `ADMIN`도 WorkLog를 작성하게 한다

Administrator가 전화로 들은 작업 내용을 대신 기록할 수 있다. 거부한 이유는 WorkLog의 `worker_id`가
"이 작업을 한 사람"을 뜻하는데, administrator가 쓰면 그 의미가 "기록한 사람"으로 흐려지고 둘을
구분할 column이 없기 때문이다. matrix도 WorkLog를 `WORKER` 항목에만 적었다. Administrator의 메모는
comment에 속한다.

### 3. WorkLog 추가를 RequestHistory에도 남긴다

하나의 timeline에서 모든 사건을 볼 수 있다. 거부한 이유는 WorkLog가 이미 durable append-only
기록이어서 같은 사실이 두 table에 중복되고, RequestHistory의 `previous_status`/`new_status`가 아무
의미도 갖지 못하는 행이 쌓이기 때문이다. Timeline이 필요하면 읽을 때 두 table을 합치면 된다.

### 4. `visitId`를 특정 status의 visit으로 제한한다

`COMPLETED` visit만 허용하면 기록이 "실제로 한 작업"에만 붙는다. 거부한 이유는 진입 실패나 취소된
visit도 서술할 가치가 있고, 그 서술이야말로 다음 작업자에게 필요한 정보이기 때문이다.

### 5. 목록을 `createdAt DESC`로 정렬해 Visit과 맞춘다

두 목록의 정렬이 같아 일관된다. 거부한 이유는 WorkLog가 작업의 서술이고 서술은 오래된 것부터 읽어야
이야기가 되기 때문이다. 일관성보다 읽는 방식이 우선이다.

### 6. Visit처럼 페이징하지 않는다

경로 둘의 모양이 같아진다. 거부한 이유는 Visit의 개수를 실질적으로 제한하는 열린 visit 하나 규칙이
WorkLog에는 없고, 저장소의 database 관례가 bounded pagination을 요구하기 때문이다.

## Consequences

이 슬라이스는 append-only aggregate를 추가하면서 request를 전혀 수정하지 않는 첫 command를
도입한다. version 검사도, status 전이도, RequestHistory 행도 없다. 그래서 V7부터 V9까지 매번 교체해
온 `ck_request_history_event_type`이 이번에는 그대로 남는다.

읽기가 staff 전용이므로 `GET`은 `RESIDENT`에게 `403`을 주는데, 이는 ADR-020이 read 경로에 세운
`404` 규칙의 예외다. 근거는 resident가 자기 request의 존재를 이미 알고 있어서 `403`이 새로 드러내는
것이 없다는 점이다. 구현은 이 두 경로가 다르게 답한다는 사실을 테스트로 덮어야 한다.

WorkLog 목록은 페이징하고 Visit 목록은 하지 않는다는 비대칭이 남는다. 근거는 위에 적었지만, 두
경로를 함께 읽는 client에게는 설명이 필요한 차이다.

Attachment, comment, request metadata PATCH는 범위 밖이다. Attachment는 S3 presigned 흐름
(ADR-008)이 얽혀 가장 크고, comment는 `CommentVisibility`와 soft-delete 정책을 함께 결정해야 한다.
