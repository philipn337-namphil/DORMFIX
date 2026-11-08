# ADR-017: MaintenanceCategory 재활성화 command

Status: Accepted - V1 authoritative decision

Date: 2026-09-30

## Context

승인된 구조 관리 정책은 비활성 상위 아래에서 하위를 생성하거나 재활성화하는 것을 거부한다고 말한다.
Frozen API table은 명시적인 category deactivate command를 정의하지만 그 짝인 재활성화는 정의하지
않는다. 그 결과 의도된 category lifecycle이 불완전하고, 현재 구현을 계약의 권위로 취급해서는 안 된다.

## Proposed decision

승인 후 다음 명시적 command를 V1 구조 관리 계약에 추가한다.

| Method | Path | Request | Authorization | Success |
|---|---|---|---|---|
| POST | `/api/v1/admin/categories/{categoryId}/reactivate` | 빈 `ReactivateCommand` | `SUPER_ADMIN` 전용 | `200 OK`와 통상 `MaintenanceCategoryResponse`, 이때 `active`는 `true` |

이 command는 비활성 category에만 유효하다. 상위가 있으면 직계 상위를 확인한다. Category나 상위가
없으면 `404 CATEGORY_NOT_FOUND`, 이미 활성이면 `409 CATEGORY_ALREADY_ACTIVE`, 상위가 비활성이면
`409 INACTIVE_PARENT`다. 마지막 것은 기존 구조 규칙과 같은 의미다. 비활성 상위 아래에서는 하위
생성이나 재활성화를 하지 않는다. 같은 command를 반복해도 조용히 멱등하게 처리하지 않는다. 그렇게
하면 상태 conflict를 숨기기 때문이다.

이 제안은 다음 기존 계약 경계를 보존한다.

- `parentId`는 생성 시에만 받고 이후 immutable하다.
- V1에는 category move endpoint가 없다.
- Category metadata PATCH는 allow-list를 유지하며 `active`, `status`, 임의 field를 받아서는 안 된다.
- 상위 비활성화는 하위 category로 전파하지 않는다.
- V1은 hard-delete endpoint를 제공하지 않는다.

## Required contract synchronization after approval

Frozen API table에 위 행을 정확히 추가해야 한다. 본문에는 응답이 통상 category 응답이며
`INACTIVE_PARENT`가 409 business-state conflict라는 점을 적는다. Frozen permission/state matrix에는
다음을 추가해야 한다. "MaintenanceCategory 재활성화는 SUPER_ADMIN 전용이며, 상위가 비활성이면
409 INACTIVE_PARENT로 거부한다. 상위 비활성화는 전파하지 않는다." 그다음 ADR-015와 ADR-016을 승인된
결정으로 동기화한다.

Frozen product 파일과 `frozen-baseline.sha256.json`은 이 승인된 결정과 동기화되어 있다.

## Alternatives

### 1. reactivate를 제공하지 않고 deactivate를 terminal로 둔다

API 표면이 줄고 비활성 category를 추론하기 쉬워진다. 그러나 확립된 하위 재활성화 정책과 충돌하고,
수정할 때마다 대체 category를 만들게 하며, 다른 구조 aggregate들과 비대칭을 남긴다. 또한 V1에서
실수로 한 비활성화를 되돌릴 수 없게 만든다.

### 2. 범용 `active` 또는 `status` PATCH를 허용한다

Client가 다뤄야 할 endpoint 형태가 줄어든다. 그러나 command 경계가 약해지고, mass assignment 실수를
허용하며, 상태별 authorization과 conflict 처리가 암묵적으로 변하고, Facility의 명시적 lifecycle
command와 일관되지 않는다. 권하지 않는다.

## Consequences

권고하는 command는 category move, cascade, delete semantics를 끌어들이지 않으면서 좁고 추적 가능한
lifecycle 전이를 제공한다. 구현을 계약 준수로 간주하거나 Frozen hash를 갱신하기 전에 승인이 필요하다.
