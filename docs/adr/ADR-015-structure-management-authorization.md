# ADR-015: Dormitory 구조 관리 authorization

Status: Accepted - V1 authoritative decision

Date: 2026-09-18

## Context

Frozen permission matrix와 API inventory는 구조 관리 role을 식별했지만 operation 수준의 정책과 MaintenanceCategory 소유권을 모호하게 남겼다. 이전 문서들도 상위 비활성화의 전파와 삭제 동작을 정의하지 않았다.

## Decision

- Dormitory, Building, Space의 생성·수정·비활성화는 `SUPER_ADMIN`만 수행할 수 있다.
- `ADMIN`은 명시적으로 할당된 Dormitory scope 안에서만 Facility를 생성·수정·비활성화할 수 있다. `SUPER_ADMIN`은 Facility를 전역으로 관리할 수 있다.
- MaintenanceCategory는 V1 전역 Master Data다. 생성·수정·비활성화는 `SUPER_ADMIN` 전용이다.
- 상위를 비활성화해도 하위가 자동으로 비활성화되지 않는다.
- 비활성 상위 아래에서 하위를 생성하거나 재활성화하는 것은 거부한다.
- 운영 데이터는 절대 hard-delete하지 않는다.
- 경로, DTO, Facility status command를 포함한 승인된 Admin API 계약은 [ADR-016](ADR-016-admin-structure-api-contract.md)에 기록한다.

## Alternatives

ADMIN이 전역 MaintenanceCategory를 관리하게 하면 전역 master-data라는 역할과 충돌하고, scope를 강제할 Dormitory FK도 없다. 비활성화를 cascade하면 aggregate를 넘어 암묵적인 상태 변경이 생긴다. 두 대안 모두 선택하지 않는다.

## Consequences

Permission matrix, API inventory, security guidance, open-question register가 이제 하나의 V1 정책을 공유한다. 관리 API를 구현하기 전에 Application authorization이 이 규칙들을 강제해야 한다. 이 ADR은 Java나 database schema 변경을 함의하지 않는다.
