# ADR-019: MaintenanceRequest 생성 계약

Status: Accepted - V1 authoritative decision

Date: 2026-10-02

## Context

Frozen V1 계약은 `POST /api/v1/maintenance-requests`, MaintenanceRequest column, 초기 lifecycle
상태인 `REPORTED`, 그리고 RESIDENT가 자신의 request를 생성한다는 점을 정의한다. 그러나 create DTO,
request-number 알고리즘, reporter와 space의 관계, 참조 객체의 상태 규칙은 정하지 않는다. 이 누락은
authorization, persistence, API error에 영향을 주므로 구현 편의로 추론해서는 안 된다.

## Decision

V1에는 resident 전용, current-residence 기반 신고 계약을 사용한다.

| 항목 | 내용 |
|---|---|
| Actor | 인증된 `RESIDENT`만. `reporterId`는 항상 JWT subject다. |
| Create DTO | `spaceId`, optional `facilityId`, `categoryId`, `title`, `description`, `entryPolicy`, `contactBeforeEntry`, optional `preferredVisitStart`, optional `preferredVisitEnd`. reporter, status, priority, duplicate, lifecycle timestamp, version field는 없다. |
| Location authorization | `spaceId`는 RESIDENT의 current Residence room이어야 한다. V1에서 resident는 공용공간이나 다른 resident의 방을 신고할 수 없다. |
| Reference validity | 방과 그 Building/Dormitory 계층이 활성이어야 한다. Category도 활성이어야 한다. `facilityId`를 제공하면 `spaceId`에 속해야 하고 `RETIRED`일 수 없다. `OUT_OF_SERVICE`는 그 상태 자체가 신고 대상이 될 수 있으므로 신고 가능하다. |
| Server-owned fields | 서버가 `status=REPORTED`, `priority=category.defaultPriority`, `duplicateOfId=null`, timestamp, 초기 `version`을 설정한다. |
| Preferred visit time | 두 timestamp가 둘 다 없거나 둘 다 있어야 하고, 있을 때는 `preferredVisitStart < preferredVisitEnd`다. 예약된 Visit이 아니라 희망 사항을 표현한다. |
| Request number | database ID가 할당된 뒤 안정적인 서버 생성 `DF-<id>` request number를 설정한다. ID 자체를 이 공개 번호의 대체물로 노출하지 않는다. |
| Audit | Create transaction은 request와, event type `REQUEST_CREATED`, actorId=reporterId, newStatus=`REPORTED`인 append-only RequestHistory 한 행을 함께 저장한다. |

생성은 `201 Created`, `/api/v1/maintenance-requests/{id}`를 가리키는 Location header, 그리고 `id`,
`requestNumber`, `status`, `priority`, `version`을 포함한 통상 request 응답을 반환한다.

없는 참조 resource는 기존 대표 코드를 사용한 `404`다. 제출된 space에 속하지 않는 facility, 비활성
계층이나 category, retired facility, reporter의 current residence 밖 space는
`409 INVALID_REQUEST_STATE`다. 잘못된 입력, 잘못된 EntryPolicy, 잘못된 preferred time 범위는 `400`이다.

## Contract synchronization

정확한 create DTO, 성공 응답, 참조 상태 규칙, request-number 알고리즘, error 의미는 Frozen API
specification과 permission matrix에 동기화한다. ERD는 생성 불변식과 `REQUEST_CREATED` audit 행을
기록한다. Frozen baseline hash는 그 계약 변경과 함께 갱신한다. Java와 Flyway 구현은 미룬다.

## Alternatives

### 1. resident가 아무 활성 Space나 신고하게 한다

별도 workflow 없이 공용공간 신고를 지원한다. 그러나 location 소유권이 약해지고 authorization이 V1에
없는 규칙에 의존하게 된다. 나중에 명시적인 공용공간 신고 경로를 만들면 지금 resident 경계를 넓히지
않고도 그 기능을 추가할 수 있다.

### 2. ACTIVE가 아닌 facility를 전부 거부한다

참조 처리가 균일해지지만, 이미 사용 중지로 표시된 facility를 resident가 신고할 수 없게 된다. Retired
facility는 더 이상 운영되지 않지만, OUT_OF_SERVICE는 신고하고 수리할 수 있는 상태를 뜻한다.

### 3. 저장 전에 무작위 공개 request number를 생성한다

두 단계 저장을 피할 수 있지만 충돌 처리와 형식 정책이 필요하다. Database ID는 이미 unique하므로
`DF-<id>`가 결정적이고 추적 가능하며 V1에 단순하다.

## Consequences

이 결정은 좁고 테스트 가능한 초기 신고 경계를 주면서 assignment, lifecycle, visit, attachment,
communication 작업을 이후 슬라이스로 남긴다. 명시적으로 요청된 이후 슬라이스에서 계약을 준수하는
구현을 허가하지만, 이 결정 자체가 Java나 Flyway 구현을 추가하지는 않는다.
