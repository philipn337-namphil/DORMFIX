# Frozen V1 권한과 state machine

Authorization은 Authentication → Role → Ownership/Assignment/Scope → Current State 순으로 평가한다. 인증된 잘못된 role, non-owner, former assignee는 403이고, authorized actor의 state/version conflict는 409다. SUPER_ADMIN도 lifecycle rule을 우회하지 않는다. ADMIN은 명시된 dormitory scope 안에서만 관리 권한을 가진다.

| Command | Source | Target | Authorized relationship |
|---|---|---|---|
| assign | REPORTED | ASSIGNED | ADMIN/SUPER_ADMIN within dormitory scope |
| reassign | ASSIGNED, IN_PROGRESS, ON_HOLD | ASSIGNED | ADMIN/SUPER_ADMIN within scope |
| start | ASSIGNED | IN_PROGRESS | current active worker |
| hold | IN_PROGRESS | ON_HOLD | current active worker |
| resume | ON_HOLD | IN_PROGRESS | current active worker |
| resolve | IN_PROGRESS | RESOLVED | current active worker |
| close | RESOLVED | CLOSED | reporter RESIDENT or scoped ADMIN/SUPER_ADMIN |
| reopen | RESOLVED | IN_PROGRESS | reporter RESIDENT or scoped ADMIN/SUPER_ADMIN |
| reject | REPORTED | REJECTED | scoped ADMIN/SUPER_ADMIN |
| mark duplicate | REPORTED | DUPLICATE | scoped ADMIN/SUPER_ADMIN; reference original |

CLOSED, REJECTED, DUPLICATE는 terminal이다. 다른 transition은 허용하지 않는다. CLOSED request는 reopen할 수 없다. Reassignment는 기존 assignment를 종료하고 새 assignment를 만들며 새 worker가 다시 start한다. Request당 `unassigned_at IS NULL` assignment는 최대 하나이고 PostgreSQL partial unique index로 보강한다.

## Role별 규칙

- RESIDENT: 본인 request 생성/조회, REPORTED에서만 수정, 본인 active request에 PUBLIC comment와 허용 attachment 추가, visit schedule 조회, RESOLVED request close/reopen. 신고 생성은 본인의 current Residence ROOM에만 가능하며 공용공간이나 다른 resident의 ROOM은 신고하지 않는다. `OUT_OF_SERVICE` Facility는 신고할 수 있지만 `RETIRED` Facility는 신고할 수 없다. status, priority, reporter, assignment를 직접 지정하지 않는다. STAFF_ONLY comment는 보지 못한다.
- WORKER: current active assignee만 start/hold/resume/resolve, valid state의 visit 관리, WorkLog, PUBLIC/STAFF_ONLY comment, repair attachment를 작성한다. Former worker는 historical read-only만 가능하다.
- ADMIN: dormitory scope의 전체 request 조회, active request의 허용된 category/priority/location 조정, assign/reassign, REPORTED reject/duplicate, visit 관리, RESOLVED close/reopen, scope 내 Facility 관리와 Facility status command 수행. Resident entry consent를 대신 변경하지 않으며 MaintenanceCategory를 관리하지 않는다.
- SUPER_ADMIN: ADMIN 기능과 전체 Dormitory/Building/Space/Facility, user-role 및 전역 system/master-data 관리를 가진다. MaintenanceCategory는 V1 전역 Master Data이므로 SUPER_ADMIN 전용이다. 모든 domain state rule은 동일하게 적용된다.

## MaintenanceVisit 세부

- Scheduling은 `ScheduleVisitRequest(scheduledStart, scheduledEnd, note?)`를 받는다. `worker_id`는
  request의 current active assignee에서 서버가 유도하고 `created_by_id`는 JWT subject다. client가
  worker를 지정하지 않는다.
- Scheduling은 active assignment와 `ASSIGNED`/`IN_PROGRESS`/`ON_HOLD` request를 요구한다. 아니면
  `409 INVALID_REQUEST_STATE`다. 시각 둘은 필수이고 `scheduledStart < scheduledEnd`여야 하며 아니면
  `400 INVALID_VISIT_TIME`다. 과거 구간은 허용한다.
- request당 `SCHEDULED` visit은 최대 하나이며 partial unique index로 보강한다. 열린 visit이 있는
  상태의 두 번째 scheduling은 `409 INVALID_REQUEST_STATE`다.
- PATCH 허용 필드는 `scheduledStart`, `scheduledEnd`, `note`뿐이다. `status`, `workerId`,
  `requestId`, actual timestamp, consent snapshot은 변경할 수 없다. `SCHEDULED`만 patch 가능하다.
- `complete`는 `actualStartedAt`과 `actualEndedAt`을 client가 둘 다 제공한다. `no-access`와
  `cancel`은 `note`를 필수로 받고 actual timestamp를 쓰지 않는다. 세 결과 모두 visit에 terminal이며
  request status를 바꾸지 않는다.
- `complete`와 `no-access`는 request가 terminal이면 `409 INVALID_REQUEST_STATE`다. `cancel`은
  `SCHEDULED` visit에 항상 가능하다.
- Scheduling, patch, 세 outcome command는 current active assignee 또는 scope 안의
  `ADMIN`/`SUPER_ADMIN`이 수행한다. WORKER이지만 current assignee가 아니면
  `403 WORKER_NOT_ASSIGNED`, 해당 command를 쓸 수 없는 role은 `403 ACCESS_DENIED`다. 인가는 state
  검사보다 먼저 평가한다. 없는 visit은 `404 VISIT_NOT_FOUND`다.
- `RESIDENT`는 visit을 읽기만 하고 목록에서 `note`를 받지 않는다. `note`는 완료·취소·진입실패
  시점의 staff free-text다. 가시성은 request read 가시성을 재사용하므로 가시성 밖 request는
  `404 REQUEST_NOT_FOUND`다. 목록은 `scheduledStart DESC, id DESC` 정렬이고 페이징하지 않는다.
- Visit command는 V1에서 version을 검사하지 않는다. Audit event는 `VISIT_SCHEDULED`,
  `VISIT_RESCHEDULED`, `VISIT_COMPLETED`, `VISIT_CANCELED`, `VISIT_NO_ACCESS`다.

## MaintenanceRequest lifecycle command 세부

- Command DTO는 `LifecycleCommandRequest(statusReason?, version)`이고 `mark-duplicate`만
  `MarkDuplicateRequest(duplicateOfId, statusReason?, version)`를 쓴다. `hold`와 `reject`는
  `statusReason`을 필수로 받고 나머지는 선택이며 최대 500자다. actor는 JWT subject다.
- `status_reason`은 현재 status의 투영이다. 매 transition이 전달된 값으로 덮어쓰고, 값이 없으면
  null로 지운다. 누적 audit은 append-only RequestHistory가 담당한다.
- `resolve`가 `resolved_at`, `close`가 `closed_at`을 쓴다. `reopen`은 `resolved_at`을 지우고
  `closed_at`은 건드리지 않는다. reopen 후 재resolve는 `resolved_at`을 덮어쓴다. `reject`와
  `mark-duplicate`는 두 timestamp를 쓰지 않는다.
- `duplicateOfId`의 대상은 존재하고 actor의 read 가시성 안에 있어야 한다. 아니면 누락과 동일한
  `404 REQUEST_NOT_FOUND`다. 자기 참조와 `DUPLICATE` 대상은 `409 INVALID_REQUEST_STATE`이며 V1은
  duplicate chain을 허용하지 않는다. `duplicate_of_id`는 한 번만 기록된다.
- `close`, `reject`, `mark-duplicate`는 같은 transaction에서 active assignment를 종료하고
  `unassigned_at`/`unassigned_by_id`에 수행자를 기록한다. `resolve`는 종료하지 않는다. `reopen`은
  active assignment가 없으면 `409 INVALID_REQUEST_STATE`다.
- `start`/`hold`/`resume`/`resolve`는 current active assignee만 수행한다. WORKER role이지만 current
  active assignee가 아닌 caller(former assignee 포함)는 `403 WORKER_NOT_ASSIGNED`, 해당 command를
  쓸 수 없는 role은 `403 ACCESS_DENIED`다. 인가는 state 검사보다 먼저 평가한다.
- matrix 밖 source state는 `409 INVALID_REQUEST_STATE`이고 command는 멱등이 아니다. 한 transaction이
  state 전이, 해당되는 경우의 assignment 종료, reason/timestamp 기록, RequestHistory 한 행을 수행한다.
- 8개 command는 `REQUEST_STARTED`, `REQUEST_HELD`, `REQUEST_RESUMED`, `REQUEST_RESOLVED`,
  `REQUEST_CLOSED`, `REQUEST_REOPENED`, `REQUEST_REJECTED`, `REQUEST_MARKED_DUPLICATE`를 남기고
  duplicate 행은 `metadata`에 `duplicateOfId`를 담는다.

## MaintenanceRequest 배정 오류 매핑

- `POST /maintenance-requests/{id}/assignments`의 actor는 request room이 해석되는 Dormitory scope의
  `ADMIN` 또는 `SUPER_ADMIN`이다. wrong role, scope 없는 ADMIN, scope 밖 ADMIN은 `403 ACCESS_DENIED`다.
- `REPORTED`는 초기 배정, `ASSIGNED`/`IN_PROGRESS`/`ON_HOLD`는 재배정이며 둘 다 결과 status는
  `ASSIGNED`다. `RESOLVED`/`CLOSED`/`REJECTED`/`DUPLICATE`는 `409 INVALID_REQUEST_STATE`다.
- 존재하지 않는 `workerId`는 `404 WORKER_NOT_FOUND`다. WORKER role이 아니거나 ACTIVE가 아닌 기존
  사용자는 `409 INVALID_WORKER`다. payload는 정상이고 실패 원인이 참조 대상의 상태이기 때문이다.
- 현재 active assignee에게 다시 배정하면 `409 REQUEST_ALREADY_ASSIGNED`다. active assignment unique
  index 경합도 같은 코드이며 `VERSION_CONFLICT`로 매핑하지 않는다.
- `409 VERSION_CONFLICT`는 submitted `version` 불일치와 request row optimistic locking 실패에만 쓴다.
- 한 transaction이 기존 assignment 종료, 신규 assignment 생성, status 전이, RequestHistory 한 행
  append를 모두 수행한다. 초기 배정은 `REQUEST_ASSIGNED`, 재배정은 `REQUEST_REASSIGNED`를 남긴다.
- V1은 worker의 Dormitory를 제한하지 않는다. `admin_dormitory_scopes`는 administrator 전용이고
  worker-dormitory mapping table이 없다.

## MaintenanceRequest 읽기 가시성

- RESIDENT는 `reporterId`가 본인인 request를 조회한다. 방을 옮겨도 본인이 올린 request는 계속 보이며,
  새 입주자는 이전 입주자의 request를 보지 못한다.
- WORKER는 본인에게 배정된 적 있는 request를 조회한다. 종료된 assignment도 포함되며, 이것이 former
  worker의 historical read-only 경로다.
- ADMIN은 request의 room이 해석되는 Dormitory가 본인 scope에 있는 request를 조회한다. SUPER_ADMIN은
  전체를 조회한다. 복수 role을 가진 caller의 가시성은 합집합이다.
- 가시성 밖 request의 단건 조회는 `404 REQUEST_NOT_FOUND`다. 존재하지 않는 경우와 응답이 같아 조회
  경로가 request의 존재를 드러내지 않는다. Command는 wrong role, non-owner, former assignee에 대해
  `403 ACCESS_DENIED`를 유지한다.
- Endpoint 자체의 role·설정 실패는 `403 ACCESS_DENIED`다. WORKER가 아닌 caller의 `assigned-to-me`,
  scope 없는 ADMIN의 listing, ADMIN scope 밖을 명시한 `dormitoryId`/`buildingId`/`spaceId` filter가
  여기 해당한다.
- Listing filter는 가시성을 좁히기만 하며 넓히지 않는다. 페이징은 0-based `page`와 `1..100` `size`,
  정렬은 고정 `createdAt DESC, id DESC`이고 client sort parameter는 V1에 없다.

## Dormitory structure administration

- Dormitory, Building, Space의 생성·수정·비활성화는 `SUPER_ADMIN`만 수행할 수 있다.
- Facility의 생성·수정·비활성화는 해당 Dormitory scope 안의 `ADMIN` 또는 `SUPER_ADMIN`이 수행할 수 있다.
- MaintenanceCategory는 V1 전역 Master Data이며 생성·수정·비활성화 모두 `SUPER_ADMIN`만 수행할 수 있다.
- Facility 상태는 `ACTIVE → OUT_OF_SERVICE → ACTIVE`를 허용하고, `ACTIVE` 또는 `OUT_OF_SERVICE`에서 `RETIRED`로 전환할 수 있다. `RETIRED`는 terminal이며 재활성화할 수 없다. 상태 명령은 `/out-of-service`, `/reactivate`, `/retire`로 분리한다.
- MaintenanceCategory의 `parentId`는 생성 시에만 설정한다. 수정 DTO에는 포함하지 않으며, V1에는 category 이동 API가 없다.
- MaintenanceCategory reactivate는 SUPER_ADMIN만 수행하며, 비활성 parent 아래 reactivation은 409 `INACTIVE_PARENT`다. parent deactivate는 하위로 전파하지 않는다.
- Residence create/detail/end와 admin list는 room Dormitory scope의 ADMIN 또는 SUPER_ADMIN만 가능하다. Admin list는 scope 밖 결과를 반환하지 않고, 명시한 scope 밖 room filter는 403이다. RESIDENT는 자신의 paged history만 조회하며 WORKER-only와 scope 없는 ADMIN은 접근할 수 없다. 기간 겹침, same-day end, non-ROOM 또는 inactive hierarchy는 409이다.
- Dormitory `name`은 V1에서 unique가 아니며 중복 이름은 409 conflict가 아니다.
- 상위 비활성화는 하위로 자동 전파하지 않는다. 비활성 상위 아래의 생성과 재활성화는 허용하지 않는다.
- 운영 데이터는 hard delete하지 않는다.

## Visit과 보존

VisitStatus는 SCHEDULED, COMPLETED, CANCELED, NO_ACCESS다. Request가 ASSIGNED, IN_PROGRESS, ON_HOLD일 때만 생성한다. Visit 완료는 request resolve가 아니다. 생성 시 `entry_policy`를 `entry_policy_snapshot`으로, `contact_before_entry`를 visit에 snapshot하여 이후 request 변경에도 immutable하게 보존한다. EntryPolicy는 RESIDENT_PRESENT_REQUIRED 또는 ABSENT_ENTRY_ALLOWED이며 contact_before_entry는 독립적이다.

User, Dormitory, Building, Space, Facility, MaintenanceCategory, MaintenanceRequest, MaintenanceAssignment, MaintenanceVisit, WorkLog, RequestHistory는 임의 hard-delete하지 않는다. Residence history를 보존하고 Comment는 soft-delete, WorkLog/RequestHistory는 append-only다. Attachment 삭제는 S3 cleanup policy와 함께 처리한다.
