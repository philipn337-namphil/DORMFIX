# Architecture review register - 미해결, baseline 보존

이 문서의 항목은 human owner가 명시적으로 승인하기 전까지 OPEN이다. Gap이나 긴장 관계를 기록한 것이며 V1을 재설계할 권한이 아니다. Historical context와 충돌하면 현재 foundation prompt와 Frozen product가 우선한다. Phase 0에서는 이를 해결하기 위한 business DDL/feature를 구현하지 않는다.

각 질문은 소유 feature slice에서 evidence와 함께 검토하고, 필요하면 proposed ADR로 올린다. 승인 전에는 기존 state, permission, ERD, API semantics를 유지한다.

## Resolved MaintenanceVisit contract

2026-11-07 human approval으로 [ADR-023](../adr/ADR-023-maintenance-visit-contract.md)의 V1 Visit
계약을 확정했다. Q07은 resolved 되었다. ERD가 "These examples are not a complete enum
declaration"으로 열어둔 history event 집합과 command DTO 수준의 공백이 아래로 닫혔다.

- `worker_id`는 client가 지정하지 않고 request의 current active assignee에서 서버가 유도한다.
  `created_by_id`는 JWT subject다. 따라서 scheduling은 active assignment를 요구한다.
- request당 `SCHEDULED` visit은 최대 하나이며 partial unique index로 보강한다.
- PATCH 허용 필드는 `scheduledStart`/`scheduledEnd`/`note`뿐이다. status, worker, actual
  timestamp, consent snapshot은 변경할 수 없다. 시간은 둘 다 또는 둘 다 아니다.
- `complete`는 actual timestamp 두 개를 client가 제공한다. `no-access`와 `cancel`은 `note`를
  필수로 받고 actual timestamp를 쓰지 않는다.
- `complete`/`no-access`는 request가 terminal이면 `409`다. `cancel`은 항상 가능해 닫힌 request에
  남은 열린 visit을 정리할 수 있다.
- 과거 시각 scheduling은 허용한다. 이미 다녀온 visit을 기록해야 하기 때문이다.
- RESIDENT는 visit 목록에서 `note`를 받지 않는다. Frozen matrix가 주는 것은 schedule이고 `note`는
  staff free-text다. 목록은 `scheduledStart DESC, id DESC` 정렬이며 페이징이 없다.
- visit command는 V1에서 version을 검사하지 않는다. ERD에 `version` 컬럼이 없다.
- 5개 event type을 추가하며 V9가 `ck_request_history_event_type`을 다시 교체한다.

전체 DTO, 권한, 가시성, 정렬, V9 물리 규칙은 Frozen API specification과 permission matrix, ERD에
동기화되어 있다.

## Resolved MaintenanceRequest lifecycle command contract

2026-11-04 human approval으로 [ADR-022](../adr/ADR-022-maintenance-request-lifecycle-command-contract.md)의
V1 lifecycle command 계약을 확정했다. Q06은 resolved 되었다. ERD가 열어둔 duplicate 정책과 command
DTO 수준의 공백이 아래로 닫혔다.

- Command DTO는 `LifecycleCommandRequest(statusReason?, version)`이고 `hold`와 `reject`는
  `statusReason`을 필수로 받는다. `mark-duplicate`는 `duplicateOfId`를 추가로 받는다.
- `status_reason`은 현재 status의 투영이며 매 transition마다 덮어쓰거나 null로 지운다. 누적 audit은
  append-only RequestHistory가 담당한다.
- `resolve`가 `resolved_at`, `close`가 `closed_at`을 쓴다. `reopen`은 `resolved_at`을 지우고
  `closed_at`은 건드리지 않는다.
- `duplicateOfId`의 대상은 존재하고 actor에게 보여야 하며, 아니면 `404 REQUEST_NOT_FOUND`다.
  자기 참조와 `DUPLICATE` 대상은 `409 INVALID_REQUEST_STATE`다. V1은 duplicate chain을 허용하지
  않는다.
- `close`/`reject`/`mark-duplicate`는 active assignment를 종료한다. `resolve`는 종료하지 않으며,
  `reopen`은 active assignment가 없으면 `409 INVALID_REQUEST_STATE`다.
- WORKER role이지만 current active assignee가 아닌 caller는 `403 WORKER_NOT_ASSIGNED`,
  해당 command를 쓸 수 없는 role은 `403 ACCESS_DENIED`다.
- 8개 event type을 추가하며 V8이 `ck_request_history_event_type`을 다시 교체한다.

전체 DTO, reason 요구사항, timestamp 정책, duplicate 규칙, 종료 전이의 assignment 처리, reopen
전제조건, 403 코드 분리, event type은 Frozen API specification과 permission matrix에, V8 물리
규칙은 ERD에 동기화되어 있다.

## Resolved MaintenanceRequest assignment contract

2026-11-04 human approval으로 [ADR-021](../adr/ADR-021-maintenance-request-assignment-contract.md)의
V1 배정 계약을 확정했다. Q05는 resolved 되었다. Frozen API가 미뤄둔
"exact validation-vs-business-conflict mapping"이 아래로 확정되었다.

- Command DTO `AssignWorkerRequest(workerId, reason?, version)`, `assignedById`는 JWT subject.
- 대상 worker 미존재는 `404 WORKER_NOT_FOUND`, WORKER role이 아니거나 ACTIVE가 아니면
  `409 INVALID_WORKER`. 생성 slice가 이미 reference state를 409로 매핑하는 선례를 따른다.
- 현재 active assignee에게 재배정하면 `409 REQUEST_ALREADY_ASSIGNED`.
  `ux_assignment_active_request` 경합도 같은 코드이며 `VERSION_CONFLICT`로 접지 않는다.
- `VERSION_CONFLICT`는 stale `version`과 request row optimistic locking 실패에만 쓴다.
- History event type을 `REQUEST_ASSIGNED`/`REQUEST_REASSIGNED`로 분리한다. V6의
  `ck_request_history_event_type`이 `REQUEST_CREATED`만 허용하므로 V7에서 교체해야 한다.
- Worker의 dormitory 제약은 V1에 두지 않는다. ADR-014가 scope row를 administrator로 한정했고
  worker-dormitory mapping table이 없다.
- `assigneeId`는 active assignment의 `worker_id`이며 denormalized column을 두지 않는다.

이 결정은 Accepted ADR-020의 구현 선행 조건이다. ADR-020의 WORKER 가시성, `assigned-to-me`,
`assigneeId` filter/field는 assignment row가 없으면 구현할 수 없고 V6에는 해당 테이블이 없으므로,
배정 slice와 조회 slice를 한 번에 구현한다. 전체 DTO와 error 의미는 Frozen API specification과
permission matrix에, V7 물리 규칙은 ERD에 동기화되어 있다.

## Resolved MaintenanceRequest query contract

2026-11-04 human approval으로 [ADR-020](../adr/ADR-020-maintenance-request-query-contract.md)의
V1 조회 계약을 확정했다. Q04는 resolved 되었다.

- RESIDENT 가시성의 기준은 current Residence room이 아니라 `reporterId`다. 방을 옮겨도 본인이 올린
  request는 계속 보인다.
- WORKER 가시성은 종료된 assignment를 포함해 former-worker historical read-only를 충족한다.
- 가시성 밖 단건 상세 조회는 `404 REQUEST_NOT_FOUND`이고, command는 `403 ACCESS_DENIED`를 유지한다.
  이 결정은 permission matrix의 "non-owner는 403"을 command에 한정해 해석하고 read 경로가 request의
  존재를 드러내지 않게 한다.
- Endpoint 자체의 role·설정 실패는 `403`이다. WORKER가 아닌 `assigned-to-me`, scope 없는 ADMIN
  listing, scope 밖을 명시한 location filter가 해당한다.
- Filter set은 optional이며 필수 filter가 없다. 페이징은 0-based `page`와 `1..100` `size`
  (default 20), 정렬은 고정 `createdAt DESC, id DESC`이고 client sort parameter는 없다.
- 목록은 summary projection, 상세는 full DTO이며 상세에 history/visit/comment/attachment를
  포함하지 않는다. 두 DTO 모두 `version`을 노출한다.

전체 DTO와 error 의미는 Frozen API specification과 permission matrix에 동기화되어 있다.

## Resolved MaintenanceRequest creation contract

2026-10-02 human approval으로 [ADR-019](../adr/ADR-019-maintenance-request-creation-contract.md)의
V1 신고 생성 계약을 확정했다.

- 신고자는 authenticated `RESIDENT`이며 `reporterId`는 JWT subject만 사용한다.
- 신고 대상은 신고자의 current Residence에 있는 ROOM뿐이다. 공용공간과 타인의 방은 V1
  신고 대상이 아니다.
- supplied Facility는 해당 Space에 속해야 하고 `RETIRED`일 수 없다. `OUT_OF_SERVICE`
  Facility는 그 상태 자체를 신고할 수 있으므로 허용한다.
- public request number는 저장 후 할당된 ID를 사용한 안정적인 `DF-<id>` 형식이다.
- 전체 DTO, reference-state, server-owned initial values, preferred time, response, error,
  and `REQUEST_CREATED` history semantics are in the Frozen product contract.

이 결정은 후속 request-create slice의 계약 기준이다. Java aggregate, API endpoint, V6
migration은 이 승인 작업에서 구현하지 않는다.

## Resolved authentication decisions

2026-09-11 human decision으로 Authentication Phase 1의 다음 항목을 확정했다. 상세 refresh-token 결정은 [ADR-013](../adr/ADR-013-refresh-token-storage.md)에 기록한다.

- Role은 `Set<Role>`로 모델링하고 `user_roles(user_id, role)`에 저장한다.
- 공개 signup에서 선택 가능한 role은 `RESIDENT`뿐이다. 일반 사용자가 privileged role을 선택하는 것은 금지한다.
- Access token은 JWT이며 수명은 30분이다.
- Refresh token은 opaque random token이며 수명은 14일이다.
- Refresh token 원문은 저장하지 않고 PostgreSQL에 SHA-256 hash만 저장한다.
- Refresh마다 rotation하고, old token 재사용은 거부한다.
- Logout은 해당 refresh token을 revoke한다.
- `SUSPENDED`와 `WITHDRAWN` 사용자는 login과 refresh를 거부한다.
- Redis와 token family는 V1에서 사용하지 않는다.

2026-09-13 human decision으로 Authentication Phase 1의 다음 세부사항을 추가 확정했다.

- Refresh token table은 `refresh_token_sessions`이며 columns는 `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at`이다. `token_hash`는 unique이고 raw refresh token은 저장하지 않는다.
- Rotation은 기존 row revoke 후 새 row 생성으로 처리한다. Token family와 `replaced_by` 같은 구조는 V1에서 사용하지 않는다.
- Access token은 `Authorization: Bearer <JWT>` header로 전달한다.
- Refresh token은 login response의 JSON body로 반환하고 refresh/logout 요청의 JSON body로 전달한다. V1에서는 cookie를 사용하지 않는다.
- Signup email은 trim 후 lowercase로 normalize한다.
- Signup은 role과 status를 입력받지 않고 서버가 각각 `RESIDENT`와 `ACTIVE`를 부여한다.
- Signup의 `name`, `email`, `password`는 필수이고 `phone`, `studentNumber`는 선택이다. `studentNumber`는 값이 존재할 경우 unique다.

위 결정으로 refresh-token 물리 table/column, rotation persistence, token 전달 방식, signup 입력·기본값·email normalization 질문은 resolved 되었다. Dormitory scope 저장 방식은 이번 Phase에서 확정하지 않으며 계속 OPEN이다. 이 결정은 임의의 추가 auth semantics를 허용하지 않는다.

## Resolved administrator dormitory scope

2026-09-18 human decision으로 Q02의 V1 저장 방식을 확정했다. 상세 결정은 [ADR-014](../adr/ADR-014-administrator-scope.md)를 따른다.

- 별도 매핑 테이블 `admin_dormitory_scopes`를 사용한다.
- `SUPER_ADMIN`은 전체 Dormitory scope를 가지며, `ADMIN`은 매핑 row가 있는 Dormitory만 관리한다.
- scope가 없는 `ADMIN`은 관리 가능한 Dormitory가 없고 전역 권한으로 해석하지 않는다. `RESIDENT`/`WORKER`에는 scope를 부여하지 않는다.
- scope 생성 시 대상 User가 `ADMIN`인지 여부는 Application layer에서 검증한다.
- PK는 `(user_id, dormitory_id)`, 추가 index는 `(dormitory_id, user_id)`이며 양쪽 FK는 ON DELETE RESTRICT다.
- scope 철회는 row 삭제로 처리하고, V1에는 active/revoked_at/history를 추가하지 않는다.

Q02는 V1 물리 저장 방식에 대해 resolved 되었다. Java Entity/Repository/API 및 실제 authorization enforcement는 후속 구현 범위다.

## Resolved structure management authorization

2026-09-18 human decision으로 Dormitory Structure 관리 권한 정책을 확정했다. 상세 결정은 [ADR-015](../adr/ADR-015-structure-management-authorization.md)를 따른다.

- Dormitory, Building, Space의 생성·수정·비활성화는 `SUPER_ADMIN`만 가능하다.
- Facility는 해당 Dormitory scope 안의 `ADMIN`과 `SUPER_ADMIN`이 관리할 수 있다.
- MaintenanceCategory는 V1 전역 Master Data이므로 `SUPER_ADMIN` 전용이다.
- 상위 비활성화는 하위로 자동 전파하지 않으며, 비활성 상위 아래 생성·재활성화는 차단한다.
- 운영 데이터의 hard delete는 허용하지 않는다.
- 정확한 Admin API path/DTO와 Facility status command는 [ADR-016](../adr/ADR-016-admin-structure-api-contract.md)로 확정했다.

## Resolved Admin structure API contract

2026-09-20 human decision으로 V1 구조 관리 API 계약을 확정했다. 상세 path, DTO, 권한, 오류 의미는 [API V1 contract](../product/api-spec-v1.md)와 [ADR-016](../adr/ADR-016-admin-structure-api-contract.md)을 따른다.

- Dormitory/Building/Space 관리 API는 `/admin/...` 계약을 사용한다.
- Dormitory `name`은 unique가 아니며 duplicate-name 409는 제공하지 않는다.
- Facility는 `/out-of-service`, `/reactivate`, `/retire` 명시적 command endpoint를 사용한다. `RETIRED`는 terminal이다.
- MaintenanceCategory `parentId`는 생성 시에만 설정하며 V1 category move API는 없다.

## Resolved dormitory structure physical rules

2026-09-16 human decision으로 Dormitory, Building, Space, Facility, MaintenanceCategory V1 migration의 최소 물리 규칙을 확정했다.

- Dormitory의 `name`, `address`, `timezone`은 NOT NULL이며 `timezone`에는 DB default를 두지 않는다.
- ERD에서 nullable로 명시된 필드만 NULL을 허용한다. `active`는 NOT NULL DEFAULT TRUE, Facility `status`는 NOT NULL DEFAULT `ACTIVE`다.
- `created_at`, `updated_at`은 모든 구조 테이블에서 NOT NULL DEFAULT CURRENT_TIMESTAMP이며 `updated_at` 자동 갱신 trigger는 사용하지 않는다.
- Building→Dormitory, Space→Building, Facility→Space, MaintenanceCategory→MaintenanceCategory FK에는 모두 ON DELETE RESTRICT를 명시한다.
- Facility `asset_code`는 nullable 일반 UNIQUE를 사용한다. Category `parent_id`는 nullable이며 `CHECK (parent_id IS NULL OR parent_id <> id)`로 자기 자신 참조만 차단한다.
- 전체 category cycle 검사는 V1 migration에서 구현하지 않는다. 상위 비활성화는 하위로 전파하지 않으며, 비활성 상위 아래 생성·재활성화 차단은 application rule로 이후 구현한다.

위 결정은 ERD의 의미와 계층을 변경하지 않으며, Dormitory → Building → Space → Facility 생성 순서와 category root/child self-reference를 위한 물리 구현 기준이다.

## Resolved Residence and Category contracts

2026-10-01 human approval accepted ADR-017 and ADR-018. Category reactivation is an explicit
SUPER_ADMIN command with inactive-parent protection. Residence uses approved half-open date
intervals, current-only create DTOs, scoped administrator list/detail/commands, self-only paged
resident reads, same-day end-then-create moves, and PostgreSQL exclusion protection for
resident-period overlap. Frozen product documents and implementation must follow the accepted
ADRs; this register no longer treats these semantics as open.
