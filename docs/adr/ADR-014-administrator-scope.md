# ADR-014: Administrator dormitory scope

Status: Accepted - V1 authoritative decision

Date: 2026-09-18

## Context

Frozen ADMIN 권한은 dormitory 범위로 한정되는데, 최초 ERD에는 administrator와 dormitory를 잇는 소속 관계가 없었다.

## Decision

V1 administrator dormitory scope에는 별도의 `admin_dormitory_scopes` mapping table을 사용한다.

- `SUPER_ADMIN`은 모든 Dormitory에 유효 scope를 가지며, 이 role에는 scope row가 필요하지 않다.
- `ADMIN`은 `admin_dormitory_scopes`로 명시적으로 연결된 Dormitory만 관리할 수 있다.
- Scope row가 없는 `ADMIN`은 관리 가능한 Dormitory가 없고, 전역 scope로 해석되지 않는다.
- `RESIDENT`와 `WORKER`에는 scope row를 부여하지 않는다.
- 대상 User가 `ADMIN`인지는 scope row를 생성할 때 Application layer에서 검증한다.
- Table의 composite primary key는 `(user_id, dormitory_id)`다.
- `(dormitory_id, user_id)`에 추가 index를 둔다.
- User와 Dormitory foreign key 모두 `ON DELETE RESTRICT`를 명시한다.
- Scope 철회는 row 삭제로 처리한다. V1에는 `active`, `revoked_at`, history column을 두지 않는다.

물리 V1 migration은 `V4__admin_dormitory_scopes.sql`이다. 구조 관리 authorization 정책은 [ADR-015](ADR-015-structure-management-authorization.md)가 정의하며, 정확한 Admin API path와 DTO는 별도 리뷰 항목으로 남는다.

## Alternatives

Resident의 Residence에서 staff 권한을 추론하는 방식은 신뢰할 수 없다. `ADMIN`을 전역으로 취급하면 권한이 조용히 넓어지므로, 두 대안 모두 선택하지 않는다.

## Trade-offs

Mapping table은 최소한의 V1 상태로 여러 dormitory를 명시적으로 관리할 수 있게 한다. 다만 revocation 이력을 보관하지 않고 role 검증을 database에 담지 않는 것은 의도된 선택이다.

## Consequences

Q02는 V1 물리 저장 방식에 대해 resolved 되었다. 이후 Application/API authorization 슬라이스를 구현할 때 domain lifecycle 규칙과 multi-role 상황의 privacy를 보존한다.
