# ADR-023: MaintenanceVisit 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-06

2026-11-07 사람 승인으로 이 계약을 제안대로 수락했다. `complete`의 actual timestamp를 client가
제공하는 것과, `RESIDENT`가 visit 목록에서 `note`를 받지 않는 것을 포함한다.

## Context

Frozen V1 계약이 이 슬라이스의 뼈대를 이미 정해두었다. ERD는 `MaintenanceVisit`의 컬럼을 준다.
`request_id`, `worker_id`, `created_by_id`, `scheduled_start`, `scheduled_end`, nullable
`actual_started_at`과 `actual_ended_at`, `status`, `entry_policy_snapshot`,
`contact_before_entry`, nullable `note`다. `VisitStatus`는 `SCHEDULED`, `COMPLETED`, `CANCELED`,
`NO_ACCESS`다. Visit은 request가 `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`일 때만 생성한다. Visit 완료는
request resolve가 아니라고 명시되어 있다. 생성 시 request의 `entry_policy`를
`entry_policy_snapshot`으로, `contact_before_entry`를 visit에 snapshot하며 이후 immutable하다.
Visit은 hard delete하지 않는다. API inventory에 여섯 경로가 있고 `INVALID_VISIT_TIME`과
`VISIT_NOT_FOUND`는 이미 frozen error code다. Permission matrix는 `RESIDENT`에게 visit schedule
조회를, current active worker와 administrator에게 valid state의 visit 관리를 준다.

Frozen 계약이 정하지 않은 것은 다음이다.

1. create DTO, 특히 `worker_id`를 client가 지정하는지 서버가 유도하는지
2. 한 request에 열린 visit이 둘 이상 존재할 수 있는지
3. consent snapshot이 immutable인 상황에서 `PATCH /visits/{visitId}`가 바꿀 수 있는 필드
4. `actual_started_at`과 `actual_ended_at`을 누가 쓰는지, client가 제공하는지
5. request가 진행된 뒤, 특히 terminal로 간 뒤에도 `complete`, `no-access`, `cancel`이 가능한지
6. scheduled 시각이 과거여도 되는지
7. `note`가 staff free-text인 상황에서 `RESIDENT`가 visit 목록에서 무엇을 보는지
8. RequestHistory event type. ERD는 `VISIT_SCHEDULED`, `VISIT_RESCHEDULED`, `VISIT_COMPLETED`를
   예시로 들면서 자신의 예시가 "not a complete enum declaration"이라고 분명히 적어, 실제 집합을
   열어두었다
9. visit command가 version을 검사하는지. ERD에 `MaintenanceVisit`의 `version` 컬럼이 없고 frozen
   version 규칙은 request command만 열거한다

## Decision

### Scheduling

`POST /maintenance-requests/{id}/visits`는 `ScheduleVisitRequest(scheduledStart, scheduledEnd,
note?)`를 받는다. `workerId`, `status`, actual timestamp, `entryPolicySnapshot`,
`contactBeforeEntry`는 절대 받지 않는다.

`worker_id`는 request의 current active assignee이며 서버가 해석한다. `created_by_id`는 JWT
subject이므로, administrator가 visit을 잡으면 그가 생성자로 기록되고 실제 수행자는 배정된 worker로
남는다. client가 worker를 지정하게 하면 "누가 이 request를 작업하는가"가 database가 이미 보장하는
단일 active assignment와 별개로 두 군데 생기고, request에 권한이 없는 worker를 visit이 지목할 수
있다.

따라서 scheduling은 active assignment를 요구한다. 없으면 `409 INVALID_REQUEST_STATE`다. Request는
`ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`여야 하며 이는 frozen 계약이 정한 것이다. 다른 status는
`409 INVALID_REQUEST_STATE`다.

`scheduledStart`와 `scheduledEnd`는 둘 다 필수이고 `scheduledStart < scheduledEnd`여야 한다.
아니면 `400 INVALID_VISIT_TIME`이다. 과거 구간도 허용한다. 작업자가 이미 다녀온 visit을 기록할 수
있어야 하고, 서버가 지난 사실의 기록을 거부할 이유가 없다.

생성은 request에서 `entry_policy_snapshot`과 `contact_before_entry`를 snapshot하고
`status=SCHEDULED`로 두며 actual timestamp 둘을 null로 남기고 RequestHistory 한 행을 append한다.

### Request당 열린 visit은 하나

한 request는 `SCHEDULED` visit을 최대 하나 가진다. 열린 visit이 있는 상태의 두 번째 scheduling은
`409 INVALID_REQUEST_STATE`다. Partial unique index로 보강한다.
`CREATE UNIQUE INDEX ux_visit_open_request ON maintenance_visit(request_id) WHERE status =
'SCHEDULED'`이며, active assignment를 보호하는 방식과 같다.

이렇게 하면 아래 worker command들이 다루는 "작업자가 지금 수행 중인 visit"이 모호해지지 않는다.
후속 visit은 현재 visit이 완료·취소되거나 진입 실패로 기록된 뒤에 잡는다.

### PATCH 허용 필드

`PATCH /visits/{visitId}`는 `UpdateVisitRequest(scheduledStart?, scheduledEnd?, note?)`를 받고
unknown field를 거부한다. `status`, `workerId`, `requestId`, actual timestamp,
`entryPolicySnapshot`, `contactBeforeEntry`는 절대 바꿀 수 없다. 뒤의 둘은 frozen 계약이 immutable로
정했고 status 변경은 named command의 소유다.

`SCHEDULED` visit만 patch할 수 있고 아니면 `409 INVALID_REQUEST_STATE`다. scheduled timestamp 두
개는 함께 제공하거나 둘 다 생략하며, 제공할 때는 `scheduledStart < scheduledEnd`를 만족해야 한다.
아니면 `400 INVALID_VISIT_TIME`이다. 한쪽만 받으면 caller가 본 적 없는 저장값과 비교해 검증하게
된다.

구간을 바꾼 patch는 `VISIT_RESCHEDULED`를 append한다. note만 바꾼 patch는 아무것도 append하지
않는다. note는 schedule 사건이 아니고 현재 문구는 visit 행 자체가 들고 있다.

### Outcome command

세 command 모두 source state는 `SCHEDULED`뿐이고 각 결과는 visit에 대해 terminal이다. 어느 것도
request status를 바꾸지 않는다. Visit 완료가 request resolve가 아니라는 것이 frozen 계약이다.

| Command | DTO | 기록 |
|---|---|---|
| `complete` | `CompleteVisitRequest(actualStartedAt, actualEndedAt, note?)` | actual timestamp 둘, `status=COMPLETED` |
| `no-access` | `VisitOutcomeRequest(note)` | `status=NO_ACCESS`, actual timestamp 없음 |
| `cancel` | `VisitOutcomeRequest(note)` | `status=CANCELED`, actual timestamp 없음 |

`complete`는 actual timestamp 둘을 필수로 받고 `actualStartedAt < actualEndedAt`이어야 한다.
아니면 `400 INVALID_VISIT_TIME`이다. 서버는 작업자가 언제 도착해 언제 떠났는지 알 수 없으므로
만들어내지 않는다. `no-access`와 `cancel`은 `note`를 필수로 받는다. 둘 다 예정된 작업이 일어나지
않았음을 기록하는데, 이유가 없으면 다음 독자가 복원할 수 없다. `no-access`는 소요 시간을 쓰지
않는다. 진입이 실패했으므로 측정할 visit이 없다.

`complete`와 `no-access`는 request가 terminal이 아니어야 하며 아니면 `409 INVALID_REQUEST_STATE`다.
닫히거나 거부되거나 중복인 request에 작업 결과를 기록하지 않는다. `cancel`은 `SCHEDULED` visit에
항상 가능하며, 이것이 request가 닫힐 때 남아버린 열린 visit을 정리하는 경로다.

### 권한

인가는 이 feature의 다른 모든 command와 마찬가지로 state 검사보다 먼저 평가한다.

Scheduling, patch, 세 outcome command는 request의 current active assignee 또는 scope 안의
`ADMIN`/`SUPER_ADMIN`이 수행한다. `WORKER`를 가졌지만 current assignee가 아닌 caller는
`403 WORKER_NOT_ASSIGNED`, 해당 command를 쓸 수 없는 role은 `403 ACCESS_DENIED`다. Scope 해석은
`DormitoryScopeResolutionService`를 재사용한다.

`RESIDENT`는 frozen matrix가 정한 대로 읽기만 가능하다.

### Visit 조회

`GET /maintenance-requests/{id}/visits`는 ADR-020의 request read 가시성을 재사용한다. request를
읽을 수 있는 caller는 그 visit 목록을 조회할 수 있고, 가시성 밖 request는 detail read와 똑같이
`404 REQUEST_NOT_FOUND`다.

`RESIDENT`는 `note`를 받지 않는다. Frozen matrix가 resident에게 주는 것은 visit *schedule*이고
`note`는 완료·취소·진입실패 시점에 적는 staff free-text다. Resident가 `STAFF_ONLY` comment를 보지
못하는 것과 같은 이유다. 그 외의 모든 것, resident 본인의 consent snapshot을 포함해 전부 보인다.

목록은 `scheduledStart DESC, id DESC`로 정렬하고 페이징하지 않는다. Request당 visit은 적고 열린
visit 하나 규칙으로 제한되며, frozen inventory는 페이징 parameter를 가진 admin listing들과 달리 이
경로에 아무 parameter도 주지 않았다.

### V1에서 version 검사는 하지 않는다

Visit command는 version을 검사하지 않는다. ERD에 `MaintenanceVisit`의 `version` 컬럼이 없고 frozen
version 규칙은 request command만 지목한다. `SCHEDULED` 전제조건과 열린 visit index 때문에 분실
갱신은 무해하다. 동시에 들어온 두 번째 `complete`는 이미 terminal인 visit을 보고 `409`를 받는다.

### Audit 행과 V9

이 슬라이스는 `VISIT_SCHEDULED`, `VISIT_RESCHEDULED`, `VISIT_COMPLETED`, `VISIT_CANCELED`,
`VISIT_NO_ACCESS`를 append한다. 각 행은 actor, 변하지 않은 request status를 두 status 필드에,
그리고 `metadata`에 `{"visitId": <id>}`를 기록한다. ERD는 앞의 셋을 예시로 들고 자신의 목록이 완전한
enum이 아니라고 적었으므로, 나머지 두 outcome event는 추론이 아니라 이 결정으로 추가한다.

V9가 `maintenance_visit`을 만들고 `ck_request_history_event_type`을 교체해 기존 11개에 새 5개를
더한다. V6, V7, V8은 수정하지 않는다.

## Contract synchronization

승인되면 네 DTO, 서버가 유도하는 `worker_id`, 열린 visit 하나 규칙, PATCH 허용 필드, actual
timestamp의 소유, `complete`/`no-access`의 terminal request 규칙, resident의 `note` 제한, 목록
정렬, 5개 event type을 Frozen API specification과 permission matrix에 동기화한다. V9 물리 규칙은
ERD note에 기록하고 frozen baseline hash를 갱신한다. 구현은 같은 요청 슬라이스에서 이어진다.

## Alternatives

### 1. client가 visit의 `workerId`를 지정하게 한다

Administrator가 assignee가 아닌 worker, 예컨대 한 번 들르는 전문가를 위해 visit을 잡을 수 있다.
거부한 이유는 database가 보장하는 단일 active assignment와 나란히, 보호되지 않는 두 번째 "누가 이
request를 작업하는가" 개념이 생기고, request에 아무 권한이 없는 worker를 visit이 지목할 수 있기
때문이다. 먼저 재배정하면 같은 의도를 한 메커니즘으로 표현한다.

### 2. request당 열린 visit을 여럿 허용한다

실제 수리는 현재 시도가 끝나기 전에 후속 방문을 잡아야 할 때가 있다. 거부한 이유는 그러면 세
outcome command가 어느 시도를 서술하는지 모호해지고, frozen 계약이 이를 요구하지도 않기 때문이다.
Partial unique index는 active assignment가 이미 의존하는 것과 같은 종류의 database 수준 보장을
준다.

### 3. `complete`에서 서버가 시계로 actual timestamp를 쓴다

필수 필드 두 개가 사라진다. 거부한 이유는 완료 호출이 작업이 끝난 순간에 일어나지 않으므로, 저장된
소요 시간이 작업자가 방에 있던 시간이 아니라 보고서를 올린 시간을 기록하게 되기 때문이다.

### 4. request가 terminal이면 모든 visit command를 거부한다

여기서 택한 분기보다 규칙 하나가 단순하다. 거부한 이유는 request를 닫으면 이미 active assignment가
종료되므로, 열린 visit이 빠져나올 경로 없이 영구히 `SCHEDULED`로 남기 때문이다. `cancel`만
허용하면 닫힌 작업에 결과를 기록하지 않고도 데이터를 정직하게 유지한다.

### 5. scheduled 구간을 미래로 강제한다

과거로 예약하는 오타를 막는다. 거부한 이유는 사후 기록이 정상 업무이고, 시계 기반 거부는 작업자가
실제 작업을 남기려고 가짜 미래 구간을 넣게 만들기 때문이다.

### 6. resident에게 `note`를 보여준다

Resident가 자기 visit이 왜 취소됐는지 알 수 있어 실제로 유용하다. 거부한 이유는 같은 필드가 완료와
진입실패 시점의 staff 기록도 담고, frozen matrix가 resident에게 주는 것은 staff 기록이 아니라
schedule이기 때문이다. Resident를 향한 취소 사유는 나중에 별도 필드로 추가할 수 있다.

## Consequences

이 슬라이스는 Visit aggregate, 이 feature의 두 번째 partial unique index, 그리고 이 저장소에서
처음으로 role에 따라 달라지는 read projection을 추가한다. 그 projection이 리뷰에서 가장 볼 가치가
있는 부분이다. 나중에 누군가 staff DTO를 재사용해 resident 경로가 `note`를 얻게 되면 안 된다.

Visit의 `worker_id`가 active assignment에서 유도되므로, scheduling과 completion 사이에 재배정이
일어나면 `worker_id`가 former assignee인 visit이 남고 command 권한은 새 assignee로 옮겨간다. 이는
올바른 이력이며, 새 assignee나 administrator가 열린 visit을 완료하거나 취소할 수 있다.

`WorkLog.visit_id`는 ERD에 이미 있고 이제 가리킬 테이블을 갖게 되지만, WorkLog 자체는 attachment,
comment, request metadata PATCH와 함께 범위 밖으로 남는다.
