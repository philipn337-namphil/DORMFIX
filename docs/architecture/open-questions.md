# Architecture review register - 미해결, baseline 보존

이 문서의 항목은 human owner가 명시적으로 승인하기 전까지 OPEN이다. Gap이나 긴장 관계를 기록한 것이며 V1을 재설계할 권한이 아니다. Historical context와 충돌하면 현재 foundation prompt와 Frozen product가 우선한다. Phase 0에서는 이를 해결하기 위한 business DDL/feature를 구현하지 않는다.

각 질문은 소유 feature slice에서 evidence와 함께 검토하고, 필요하면 proposed ADR로 올린다. 승인 전에는 기존 state, permission, ERD, API semantics를 유지한다.

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
