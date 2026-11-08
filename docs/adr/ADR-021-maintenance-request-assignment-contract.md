# ADR-021: MaintenanceRequest 배정 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-04

2026-11-04 사람 승인으로 이 계약을 제안대로 수락했다. `409 INVALID_WORKER` 매핑, 중복 assignee
command와 active-assignment 경합 모두에 대한 `409 REQUEST_ALREADY_ASSIGNED`, 그리고 denormalized
`assignee_id` column 없이 assignment table을 단일 진실 원천으로 두는 것을 포함한다.

## Context

Frozen V1 계약은 이전 슬라이스들보다 이 슬라이스를 더 많이 정해두었다. API inventory는 초기 배정과
재배정을 위해 `POST /maintenance-requests/{id}/assignments`를 열거하고, 이 경로가 submitted entity
version을 요구한다고 기록한다. Permission matrix는 전이를 확정한다. `assign`은 `REPORTED`를
`ASSIGNED`로 옮기고, `reassign`은 `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD`를 다시 `ASSIGNED`로 옮기며,
둘 다 dormitory scope 안의 `ADMIN` 또는 `SUPER_ADMIN`이 수행하고, 재배정은 기존 assignment를 종료한
뒤 새 assignment를 만들며 새 worker가 다시 start해야 한다. ERD는 table 모양을 확정한다. `id`,
`request_id`, `worker_id`, `assigned_by_id`, `assigned_at`, nullable `unassigned_at`, nullable
`unassigned_by_id`, nullable `reason`이다. 그리고 물리적 guard의 이름도 준다.
`CREATE UNIQUE INDEX ux_assignment_active_request ON maintenance_assignment(request_id) WHERE
unassigned_at IS NULL`이다.

Frozen 계약이 의도적으로 열어둔 것은 error 매핑이다. `REQUEST_ALREADY_ASSIGNED`, `INVALID_WORKER`,
`WORKER_NOT_FOUND`, `WORKER_NOT_ASSIGNED`를 대표 code로 지목한 뒤, 정확한
validation 대 business-conflict 매핑은 assignment API review에서 정해야 한다고 분명히 말하고, 모든
integrity 제약 오류를 `VERSION_CONFLICT`로 매핑해서는 안 된다고 경고한다. 또한 다음도 정하지 않는다.

1. assignment command의 request DTO
2. 지목된 사용자가 유효한 배정 대상이 되는 조건, 그리고 worker가 dormitory로 제한되는지
3. 지목된 worker가 이미 active assignee인 경우의 처리
4. `ux_assignment_active_request` 경합이 어떤 error를 내는지
5. 배정과 재배정의 RequestHistory event type. V6 check 제약이 현재 `REQUEST_CREATED`만 허용하므로
   중요하다
6. read 슬라이스가 `assigneeId`를 어떻게 유도하는지

ADR-020은 승인되었으나 단독으로 구현할 수 없다. `WORKER` 가시성 규칙, `assigned-to-me` 경로,
`assigneeId` filter, `assigneeId` read field가 모두 assignment row를 필요로 하는데 V6는 assignment
table도 `assignee_id` column도 만들지 않았다. 따라서 이 슬라이스가 먼저 들어가 read 계약 전체를
가능하게 한다.

## Decision

### Command와 actor

`POST /maintenance-requests/{id}/assignments`는 정확히
`AssignWorkerRequest(workerId, reason?, version)`를 받는다. `status`, `assignedById`, `assignedAt`,
`unassignedAt`, assignment 식별자는 절대 받지 않는다. `assignedById`는 항상 JWT subject다.

Actor는 request의 방이 해석되는 dormitory를 scope에 가진 `ADMIN`, 또는 `SUPER_ADMIN`이다. Scope
해석은 `DormitoryScopeResolutionService`를 재사용한다. 잘못된 role, scope 없는 `ADMIN`, request
dormitory 밖의 `ADMIN`은 `403 ACCESS_DENIED`다.

Actor가 행동하려면 이미 그 request를 볼 수 있어야 하므로, 이 경로는 authorization 실패에 `403`을
유지한다. ADR-020의 `404` 규칙은 read 경로에만 적용된다.

### 한 경로, 두 동작, 현재 상태가 선택한다

| 현재 status | 동작 | 결과 status |
|---|---|---|
| `REPORTED` | 초기 배정 | `ASSIGNED` |
| `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD` | 재배정: active assignment를 종료하고 새로 생성 | `ASSIGNED` |
| `RESOLVED`, `CLOSED`, `REJECTED`, `DUPLICATE` | 거부 | 변경 없음 |

거부된 상태는 `409 INVALID_REQUEST_STATE`다. `RESOLVED`를 거부하는 이유는 matrix가 이를 재배정
source로 열거하지 않기 때문이다. Resolved request는 먼저 reopen한다. `CLOSED`, `REJECTED`,
`DUPLICATE`는 terminal이며 `SUPER_ADMIN`도 우회하지 않는다.

재배정은 `IN_PROGRESS`나 `ON_HOLD`에서도 request를 `ASSIGNED`로 되돌리므로 새 worker가 작업을 다시
시작한다. 이는 matrix 규칙이고 이 슬라이스가 만든 것이 아니다.

### 유효한 배정 대상

지목한 `workerId`는 존재해야 하며, 아니면 `404 WORKER_NOT_FOUND`다. 존재하지만 `WORKER` role이
없거나 status가 `ACTIVE`가 아닌 사용자는 `409 INVALID_WORKER`다.

`INVALID_WORKER`를 `400`이 아니라 `409`로 두는 이유는 payload가 정상이고 실패 원인이 참조 대상의
상태이기 때문이다. 이는 생성 슬라이스가 이미 retired facility나 비활성 category를 매핑하는 방식과
같다. `400`은 잘못된 입력과 field validation 실패에 남긴다.

V1은 worker가 어느 dormitory에 배정될 수 있는지 제한하지 않는다. 승인된 ADR-014 결정에 따라
`admin_dormitory_scopes`는 administrator 전용이고 worker와 dormitory를 잇는 mapping이 없다. 모든
`ACTIVE` `WORKER`는 actor가 배정할 권한을 가진 어떤 request에든 배정될 수 있다.

### 이미 그 worker에게 배정된 경우, 그리고 active-assignment 경합

이미 active assignee인 사용자에게 다시 배정하는 것은 `409 REQUEST_ALREADY_ASSIGNED`다. 조용한
no-op으로 처리하는 것이 거부보다 나쁘다. 오해를 부르는 history 행을 남기고, `IN_PROGRESS` request를
`ASSIGNED`로 되돌려 작업이 시작되었다는 사실을 버리게 된다.

`ux_assignment_active_request` 경합에서 진 동시 command도 `409 REQUEST_ALREADY_ASSIGNED`다. Frozen
계약은 모든 integrity 위반을 `VERSION_CONFLICT`로 접는 것을 명시적으로 금지하고, 이 index 위반은
정확히 active assignment가 이미 존재한다는 뜻이다. `VERSION_CONFLICT`는 submitted `version`이 낡은
경우와 request row의 optimistic-locking 실패에 남긴다.

### Version과 원자성

Submitted `version`은 command 실행 전에 request와 비교한다. 불일치는 `409 VERSION_CONFLICT`다.
Flush 시점의 optimistic-locking 실패도 `409 VERSION_CONFLICT`다.

Application transaction 하나가 command 전체를 수행한다. 재배정 시 active assignment에
`unassigned_at`과 `unassigned_by_id`를 설정하고, 새 assignment row를 insert하고, request status를
`ASSIGNED`로 옮기고, append-only RequestHistory 한 행을 append한다. 그 transaction 안에서 network
호출은 하지 않는다. 어디서든 실패하면 전부 rollback하므로, request가 history 행 없이 assignment를
얻는 일은 없다.

### Audit 행

초기 배정은 event type `REQUEST_ASSIGNED`를, 재배정은 `REQUEST_REASSIGNED`를 append한다. 둘 다
`actor_id`를 수행 administrator로, `previous_status`와 `new_status=ASSIGNED`를 기록한다. `metadata`는
`{"workerId": <id>}`를 담고 재배정은 `"previousWorkerId"`도 담는다. Event type을 둘로 나누면 audit이
읽기 쉽고, check 제약이 이 슬라이스가 쓰는 것을 정확히 열거할 수 있다.

`reason`은 제공되면 최대 500자이며 새 assignment row에 저장한다. 종료된 행은 생성 당시의 reason을
유지하고 `unassigned_by_id`를 얻는다.

### V7 migration

V7은 ERD가 정한 대로 `maintenance_assignment`를 만든다. 네 개의 user와 request foreign key 모두
`ON DELETE RESTRICT`, `assigned_at`은 `NOT NULL DEFAULT CURRENT_TIMESTAMP`, partial unique index
`ux_assignment_active_request`, 그리고 `assigned-to-me`를 위한 `(worker_id, assigned_at DESC)`
index를 둔다. V7은 또한 `ck_request_history_event_type`을 교체해 `REQUEST_CREATED`,
`REQUEST_ASSIGNED`, `REQUEST_REASSIGNED`를 허용하게 한다. V6는 수정하지 않고 새 migration에서 제약을
drop한 뒤 재생성한다.

### Read 계약에 미치는 영향

ADR-020의 두 read DTO에 있는 `assigneeId`는 active assignment의 `worker_id`, 즉 `unassigned_at IS
NULL`인 행의 값이며 request에 그런 행이 없으면 `null`이다. `WORKER` read 가시성은 caller에 대한
assignment row가 종료되었든 활성이든 일치하면 성립하고, `active=false`인 `assigned-to-me`는 종료된
행을 선택한다. Assignment table이 단일 진실 원천으로 남고 request row는 denormalized assignee
column을 갖지 않는다.

## Contract synchronization

승인되면 assignment DTO, actor 규칙, 상태 표, 대상 유효성 규칙, `REQUEST_ALREADY_ASSIGNED`와
`INVALID_WORKER` 매핑, `VERSION_CONFLICT`의 경계, 두 history event type을 Frozen API specification과
permission matrix에 동기화하고, V7 물리 규칙을 ERD note에 기록하며, frozen baseline hash를 갱신한다.
구현은 ADR-020 read endpoint와 같은, 명시적으로 요청된 슬라이스에서 이어진다.

## Alternatives

### 1. assign과 reassign에 별도 endpoint를 둔다

경로 두 개면 각 command의 전제조건이 URL에 드러난다. Frozen inventory는 둘에 하나의 경로를
정의하고, 현재 status가 이미 동작을 모호함 없이 선택한다. Frozen 경로를 바꾸지 않기 위해 거부한다.

### 2. `maintenance_request`에 `assignee_id` column을 비정규화한다

Read 슬라이스가 active assignment를 join하는 대신 평범한 column 읽기가 되고 listing query가 단순해
진다. 거부한 이유는 partial unique index가 보호하지 않는 두 번째 진실 원천이 생기고, column과
assignment 이력의 불일치가 조용히 발생하기 때문이다. 대가는 `assigneeId`를 투영하는 모든 read가
active 행을 join하거나 sub-select한다는 점이며, partial unique index가 이를 지원한다.

### 3. active-assignment 경합을 `VERSION_CONFLICT`로 매핑한다

두 번째 conflict code가 필요하지 않다. Frozen 계약은 `REQUEST_ALREADY_ASSIGNED`를 지목하고 모든
integrity 위반을 `VERSION_CONFLICT`로 매핑하는 것을 명시적으로 경고하며, 두 조건은 실제로 다르다.
하나는 caller가 낡은 것이고 다른 하나는 경쟁하는 배정이다.

### 4. `INVALID_WORKER`를 `400`으로 매핑한다

Worker가 아닌 대상은 잘못된 field 값으로 읽을 수도 있다. 일관성을 위해 거부한다. 생성 슬라이스는
정상 형식의 참조가 사용 불가 상태일 때 이미 `409`로 답하는데, `400`으로 두면 같은 종류의 실패가 두
슬라이스에서 다르게 답하게 된다.

### 5. 현재 assignee에게 배정하는 것을 멱등 성공으로 처리한다

재시도하는 client에게 친절하다. 거부한 이유는 `IN_PROGRESS`나 `ON_HOLD`를 `ASSIGNED`로 되돌리고
일어나지 않은 변경을 서술하는 history 행을 남기기 때문이다.

### 6. 배정 전에 worker와 dormitory의 mapping을 요구한다

Worker를 그가 담당하지 않는 dormitory에 배정하는 일을 막는다. 그런 table이 없고, ADR-014가 scope
row를 administrator로 의도적으로 한정했으며, 여기서 mapping을 만들어내면 V1이 승인된 범위를 넘어
확장된다. 이 command를 바꾸지 않고 나중 슬라이스에서 추가할 수 있다.

## Consequences

이 슬라이스는 assignment aggregate, 생성 이후 첫 lifecycle 전이, 그리고 frozen version-checked
command protocol의 첫 사용을 추가한다. 승인된 ADR-020 read 계약을 온전히 가능하게 하므로 read
endpoint와 assignment command를 함께 구현한다.

Assignment table을 단일 진실 원천으로 두면 `assigneeId`는 모든 read 투영에서 join 비용을 쓴다.
Event type 둘은 V6 check 제약을 V7에서 교체해야 한다는 뜻이고, 이후 모든 lifecycle 슬라이스가 같은
방식으로 이를 확장할 것이다.

Start, hold, resume, resolve, close, reopen, reject, mark-duplicate, visit, work log, attachment,
comment는 범위 밖이다. `WORKER_NOT_ASSIGNED`는 그 worker command들을 위해 frozen error 표에 지목되어
있고 이 슬라이스는 사용하지 않는다.
