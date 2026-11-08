# ADR-016: Admin 구조 API 계약

Status: Accepted - V1 authoritative decision

Date: 2026-09-20

## Context

구조 관리 authorization 정책은 ADR-015에서 승인되었으나, Admin API inventory는 정확한 관리 경로, DTO의 변경 가능 범위, Facility lifecycle command를 여전히 열어두었다. Java Controller와 Application Service를 구현하기 전에 API 계약을 확정해야 한다.

## Decision

- Dormitory, Building, Space, Facility, MaintenanceCategory 관리에는 [V1 API contract](../product/api-spec-v1.md)에 기록된 `/admin/...` 경로와 allow-list된 request DTO를 사용한다.
- Dormitory/Building/Space 관리는 제안된 create, metadata update, deactivate endpoint를 사용한다. Dormitory `name`에는 V1 unique 제약이 없고 중복 이름은 409 conflict로 매핑하지 않는다.
- Facility status 변경은 명시적 command다. `/out-of-service`, `/reactivate`, `/retire`를 쓴다. 허용되는 전이는 `ACTIVE -> OUT_OF_SERVICE -> ACTIVE`, `ACTIVE -> RETIRED`, `OUT_OF_SERVICE -> RETIRED`다. `RETIRED`는 terminal이다. Facility PATCH는 status를 바꿀 수 없다.
- MaintenanceCategory `parentId`는 create DTO만 받는다. Update DTO는 받지 않으며 V1에는 category move endpoint가 없다.
- 나머지 경로, DTO field, role, scope 규칙, 표준 error 계약은 V1 API와 permission 문서에 기록된 그대로다. 이 문서 결정은 Java Controller나 Application Service를 함의하지 않는다.

## Alternatives

임의의 status PATCH를 허용하면 terminal 상태 강제가 암묵적으로 변하고 command 경계가 약해진다. Category move endpoint를 제공하면 필요해지기 전에 cycle, 상위 상태, authorization semantics가 추가로 생긴다. Dormitory 이름 unique를 강제하면 V1이 요구하지 않는 product 규칙이 늘어난다.

## Consequences

Client는 안정적이고 명시적인 Facility command와, 생성 이후 변하지 않는 category 상위 관계에 의존할 수 있다. 구현 슬라이스는 문서화된 전이, scope authorization, error 의미를 강제해야 하며 범용 status/delete endpoint를 추가하지 않는다.
