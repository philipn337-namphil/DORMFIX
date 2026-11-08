# ADR-018: Residence 관리 계약

Status: Accepted - V1 authoritative decision

Date: 2026-10-01

## Decision

Residence는 독립적이고 이력이 immutable한 aggregate다. `User`와 `Space`를 ID로만 참조한다. 기간은
`[startDate, endDate)`다. `startDate`는 포함이고, null이 아닌 `endDate`는 입주자가 그 방에 더는 살지
않는 첫 local date다. 날짜 `D`에 current인 조건은 `startDate <= D`이고 (`endDate`가 null이거나
`D < endDate`)이다. `D`는 참조된 방의 Dormitory timezone으로 계산하며 값은 `DATE`로 저장한다.

생성은 current residence만 지원한다. `CreateResidenceRequest`는 정확히 `residentId`,
`roomSpaceId`, `startDate`이며 `endDate = null`로 생성한다. `startDate`는 오늘이거나 그 이전일 수
있고 이후일 수는 없다. V1에는 닫힌 과거 row의 직접 생성, 범용 PATCH, hard delete, 원자적 move
endpoint가 없다. 종료는 아래 named command만으로 한다.

한 입주자에게 기간이 겹치는 Residence는 있을 수 없다. V1에는 수용 인원 계약이 없으므로 여러
입주자가 같은 ROOM을 동시에 점유할 수 있다. 종료된 Residence는 immutable하다. 종료는 `endDate`를
현재 Dormitory 날짜로 설정하며 `startDate < today`를 요구한다. 같은 날 생성한 Residence를 같은
날짜에 종료할 수는 없다. 같은 날 이동은 원자적이지 않은 두 command로 허용한다. 더 이전의 current
Residence를 종료한 뒤 오늘 시작하는 새 current Residence를 생성한다.

대상 사용자는 RESIDENT role을 가져야 한다. 대상 Space는 활성 Building과 Dormitory 아래의 활성
ROOM이어야 한다. Residence는 어떤 MaintenanceRequest 변경이나 entry-consent 변경도 제공하지 않는다.

## API contract

모든 응답은 `id`, `residentId`, `roomSpaceId`, `startDate`, `endDate`, `createdAt`을 가진 DTO다.
`ResidencePageResponse`는 `content`, `page`, `size`, `totalElements`, `totalPages`를 가진다.

| Method | Path | Request/result | Authorization |
|---|---|---|---|
| POST | `/api/v1/admin/residences` | `CreateResidenceRequest(residentId, roomSpaceId, startDate)`; 201 ResidenceResponse | 해당 방 scope의 ADMIN 또는 SUPER_ADMIN |
| GET | `/api/v1/admin/residences/{residenceId}` | ResidenceResponse | 해당 Residence 방 scope의 ADMIN 또는 SUPER_ADMIN |
| GET | `/api/v1/admin/residences?residentId=&roomSpaceId=&current=&page=&size=` | ResidencePageResponse | scoped ADMIN 또는 SUPER_ADMIN |
| POST | `/api/v1/admin/residences/{residenceId}/end` | 빈 command; 200 ResidenceResponse | 해당 Residence 방 scope의 ADMIN 또는 SUPER_ADMIN |
| GET | `/api/v1/me/residences?current=&page=&size=` | 본인 ResidencePageResponse | 인증된 RESIDENT 전용 |

Admin list는 `residentId`, `roomSpaceId`, `current` 중 하나 이상을 요구한다. `size`는 1~100이고
정렬은 `startDate DESC, id DESC`다. SUPER_ADMIN은 전체 결과를 본다. ADMIN은 자신의 Dormitory scope
안의 방만 본다. Scope 밖을 명시한 `roomSpaceId`는 403이고 scope 없는 ADMIN은 403이며, scope 밖
레코드는 절대 포함되지 않는다. Self endpoint에는 사용자 선택자가 없다. WORKER는 실제 role 집합에
RESIDENT도 포함된 경우에만 사용할 수 있다.

| Status | 의미 |
|---|---|
| 400 | 잘못된 DTO나 날짜, `endDate` 같은 미허용 create field, filter 누락, 잘못된 pagination |
| 403 | role 부족, 필요한 dormitory scope 부족, self 관계 아님 |
| 404 | Residence, User, Space, 참조된 계층 레코드가 없음 |
| 409 | 기간 겹침, current가 아니거나 같은 날 종료, non-ROOM, 비활성 계층 |

## Persistence and concurrency

Flyway가 table을 소유하며 BIGINT identity key, `DATE` 기간 column, TIMESTAMPTZ `created_at`,
제한적인 foreign key를 쓴다. PostgreSQL `btree_gist`와 `resident_id WITH =` 및
`daterange(start_date, end_date, '[)') WITH &&`에 대한 exclusion 제약이 겹침에 대한 race-safe한
최종 방어선이다. Application 검증은 읽기 쉬운 오류를 주고 그 named 제약을
`409 RESIDENCE_PERIOD_OVERLAP`으로 매핑한다.

## Consequences

Controller는 DTO만 매핑하고 application service가 각 command transaction을 소유한다. 다른 aggregate
graph, 범용 status endpoint, Phase 3 workflow는 끌어들이지 않는다.
