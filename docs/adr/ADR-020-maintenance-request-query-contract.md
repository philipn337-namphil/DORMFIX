# ADR-020: MaintenanceRequest 조회 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-04

2026-11-04 사람 승인으로 이 계약을 제안대로 수락했다. 가시성 밖 단건 조회의 `404` 응답과 resident
가시성의 `reporterId` 기준을 포함한다.

구현 의존성: `WORKER` 가시성 규칙, `GET /maintenance-requests/assigned-to-me`, `assigneeId` filter,
`assigneeId` read field는 모두 assignment row를 필요로 한다. V6는 `maintenance_request`와
`request_history`만 만들었으므로 assignment table이나 `assignee_id` column이 아직 없다. 따라서 이
계약은 assignment 슬라이스보다 먼저가 아니라 그와 함께 구현한다. Assignment schema와 semantics는
ADR-021에서 결정한다.

## Context

Frozen V1 API inventory는 MaintenanceRequest read 경로 세 개를 이미 열거한다.
`GET /maintenance-requests`(scoped listing), `GET /maintenance-requests/assigned-to-me`(current
worker listing), `GET /maintenance-requests/{id}`(scoped detail)다. Permission matrix는 role 의도를
말한다. `RESIDENT`는 자신의 request를 생성하고 조회하며, `ADMIN`은 자신의 dormitory scope에 있는
모든 request를 조회하고, `SUPER_ADMIN`은 전역으로 조회하며, former `WORKER`는 historical read-only
접근을 유지한다.

Frozen 계약은 거기서 멈춘다. 다음은 정하지 않는다.

1. `RESIDENT` 가시성의 기준이 reporter인지 caller의 current Residence room인지
2. caller 가시성 밖 request의 단건 조회가 `403`인지 `404`인지
3. 목록 filter parameter 집합과, filter를 최소 하나 요구하는지 여부
4. 페이징 경계와 기본값, 정렬 순서
5. 정확한 read DTO field와 summary 대 detail의 분리
6. detail이 RequestHistory, Visit, WorkLog, comment, attachment를 포함하는지
7. 이미 종료된 assignment에 대해 `assigned-to-me`가 무엇을 뜻하는지
8. scope가 없는 `ADMIN`과 명시적으로 scope 밖인 filter 값에 대한 동작

Frozen API specification은 이들을 의도적으로 미뤘다. 향후 paged query는 명시적 DTO, 안정적 정렬,
제한된 size를 쓰도록 요구하면서, query와 filter field, 공개 history projection은 슬라이스 계약에
맡겼다. 이 항목들은 authorization 경계, 정보 노출, index 요구사항을 결정하므로 구현 편의로
추론해서는 안 된다.

## Decision

V1에는 가시성을 기준으로 삼고, 범위가 제한되며, 정렬이 고정된 read 계약을 사용한다.

### Read 가시성

| Role | 보이는 request | 기준 |
|---|---|---|
| RESIDENT | caller가 신고한 request | `reporterId`가 JWT subject와 같음 |
| WORKER | caller가 배정되었거나 배정된 적 있는 request | caller에 대한 assignment row, 현재든 종료든 |
| ADMIN | request의 방이 caller scope의 dormitory로 해석되는 request | `admin_dormitory_scopes`, Space에서 Building, Dormitory로 해석 |
| SUPER_ADMIN | 모든 request | 전역 |

`RESIDENT` 가시성은 caller의 current Residence room이 아니라 `reporterId`를 기준으로 한다. 방을
옮긴 resident는 자신이 신고한 request에 계속 접근할 수 있고, 이는 frozen `close`와 `reopen` 권한이
의존하는 조건이다. 또한 새 입주자가 이전 입주자의 신고를 물려받지 않는다.

`WORKER` 가시성은 종료된 assignment를 포함하며, 이것이 frozen의 former-worker historical read-only
규칙이 요구하는 바다. Dormitory scope 해석은 두 번째 순회를 만들지 않고 기존
`DormitoryScopeResolutionService`를 재사용한다. Caller가 여러 role을 가지면 가시성은 그 행들의
합집합이다.

### 가시성 밖 단건 조회는 404

`GET /maintenance-requests/{id}`는 request가 존재하지 않을 때와 caller 가시성 밖에 존재할 때 모두
`404 REQUEST_NOT_FOUND`를 반환한다. Request ID는 순번이고 추측 가능하므로, 이 경로에서 `403`을
주면 존재 여부를 알려주는 oracle이 된다. 인증만 된 resident가 ID를 훑어 request가 몇 개 있고 어느
것이 살아 있는지 알아낼 수 있다.

이는 non-owner가 `403`이라는 matrix 문장을 모순시키지 않고 정교화한다. 그 규칙은 command를
규율하는데, command에서는 actor가 시스템이 이미 존재를 알려준 resource에 대해 행동한다. 모든
command는 `403 ACCESS_DENIED`를 유지한다.

`404` 규칙은 ID별 detail read에만 적용된다. Endpoint 수준의 role과 설정 오류는 존재 탐색이 아니므로
`403 ACCESS_DENIED`로 남는다. `WORKER`가 아닌 caller의 `assigned-to-me` 호출, scope 없는 `ADMIN`의
listing 호출, `ADMIN` scope 밖을 명시한 `dormitoryId`, `buildingId`, `spaceId` filter가 여기
해당한다. 이는 list endpoint에 대한 Residence 선례와 일치한다.

### Listing, filter, 페이징, 정렬

`GET /maintenance-requests?status=&priority=&categoryId=&dormitoryId=&buildingId=&spaceId=&assigneeId=&page=&size=`

- `status`는 반복 가능하고 나머지는 단일 값이다. 전부 optional이다.
- 필수 filter는 없다. Read 가시성이 이미 모든 결과 집합을 제한하고 `size`에 상한이 있으며, request
  목록은 administrator의 주요 작업 큐라서 filter 없이도 쓸 수 있어야 하기 때문이다. 이 점이 admin
  Residence 목록과 다르다.
- Filter는 가시성 안에서 좁히기만 하고 넓히지 않는다. Caller가 볼 수 없는 resource를 지목한 filter는
  빈 page를 주며, 위의 scope 경우만 `403`이다.
- 해석할 수 없는 `status`나 `priority`는 `400 VALIDATION_FAILED`다.
- `page`는 0부터 시작하고 기본값은 `0`이다. `size`는 `1..100`이고 기본값은 `20`이다. 위반은
  `400 VALIDATION_FAILED`이며 기존 Residence 페이징 guard와 일관된다.
- 정렬은 `createdAt DESC, id DESC`로 고정한다. V1은 client sort parameter를 노출하지 않는다. 정렬은
  Spring Data가 Java property 이름을 덧붙일 수 없는 형태로 표현해야 한다. Residence 슬라이스가 이미
  native 정렬에서 바로 그 결함을 고쳐야 했다.

`GET /maintenance-requests/assigned-to-me`는 `WORKER` 전용이며 같은 page envelope, DTO, 경계, 정렬을
사용한다. `active` parameter는 기본값 `true`로 caller의 현재 assignment row(`unassigned_at IS NULL`)를
선택하고, `active=false`는 종료된 assignment를 선택한다. 이것이 former assignee가 historical
read-only 조회에 도달하는 경로다.

### Read DTO

Listing은 summary projection을 반환한다. `id`, `requestNumber`, `status`, `priority`, `categoryId`,
`spaceId`, `facilityId`, `title`, `assigneeId`, `createdAt`, `updatedAt`, `version`이다.

Detail은 summary field에 `reporterId`, `description`, `entryPolicy`, `contactBeforeEntry`,
`preferredVisitStart`, `preferredVisitEnd`, `duplicateOfId`, 그리고 V6가 저장하는 lifecycle
timestamp를 더한다.

`description`과 출입 관련 field는 의도적으로 summary에서 뺀다. 목록 payload를 무제한으로 만들고,
matrix가 resident 소유로 다루는 출입 동의 데이터를 벌크 listing에 퍼뜨리기 때문이다.

Detail은 RequestHistory, Visit, WorkLog, comment, attachment collection을 포함하지 않는다. 각각 자기
경로와 자기 가시성 규칙을 가진다. 특히 resident는 `STAFF_ONLY` comment를 절대 받지 않는다. 이들을
포함하면 그 규칙들을 request read 경로 안에 복제하게 되는데, 그곳이야말로 노출 결함의 대가가 가장
큰 자리이고, detail payload도 무제한이 된다.

Page envelope는 기존 형태를 재사용한다. `content`, `page`, `size`, `totalElements`, `totalPages`다.
`version`은 항상 노출한다. Frozen 계약이 version을 검사하는 command를 위해 request read DTO가 이를
노출하도록 요구하기 때문이다.

### 경계와 transaction

Read는 read-only transaction에서 실행한다. 응답은 명시적 record를 쓰고 managed JPA entity를 HTTP
경계에서 직렬화하지 않는다. Controller는 application query service만 호출하고 transaction이나
repository 접근을 소유하지 않는다. 가시성 조건은 적재된 page를 걸러내는 방식이 아니라 query에서
평가하므로 `totalElements`가 정확하다.

### Error

`404 REQUEST_NOT_FOUND`, `403 ACCESS_DENIED`, `400 VALIDATION_FAILED`는 모두 기존 frozen code다. 이
계약은 새 error code를 도입하지 않는다.

## Contract synchronization

승인되면 read DTO, filter 집합, 페이징 경계, 고정 정렬, 가시성 기준, read와 command의 `404`/`403`
규칙, error 매핑을 Frozen API specification과 permission matrix에 동기화한다. Java 구현은 별도로
요청된 슬라이스로 미룬다.

## Alternatives

### 1. 가시성 밖 단건 조회에 403을 반환한다

Residence 선례와 matrix 문장 그대로에 부합하고, read와 command에 한 규칙을 유지한다. 거부한 이유는
request ID가 순번이어서 `GET /{id}`가 인증된 아무 사용자에게나 존재와 총량을 알려주는 oracle이 되기
때문이다. 택한 분기는 caller가 이미 resource의 존재를 아는 곳에만 `403`을 남긴다.

### 2. RESIDENT 가시성을 current Residence room 기준으로 한다

새 입주자가 자기가 옮겨온 방의 미해결 문제를 볼 수 있어 실제 운영 가치가 있다. 거부한 이유는 방을
옮긴 사람이 자신이 신고한 request에 대한 접근을 조용히 잃고, 그 request에 붙은 frozen `close`와
`reopen` 권한이 깨지며, 이전 입주자의 신고가 노출되기 때문이다. 나중에 명시적인 방 이력 경로를
만들면 지금 resident 가시성을 넓히지 않고도 그 운영 요구를 충족할 수 있다.

### 3. client가 지정하는 sort parameter를 받는다

UI에 유연하다. 그러나 allow-list가 필요하고 sort key마다 index 확보가 필요하며, 이후 keyset
pagination으로 가기 어려워진다. V1은 Residence와 같이 안정적인 정렬 하나를 고정한다.

### 4. detail 응답에 history, visit, comment를 포함한다

Detail 화면을 한 번의 왕복으로 처리한다. 거부한 이유는 각 하위 resource의 가시성 규칙을 request
read 경로에 복제하고 payload가 무제한이 되기 때문이다.

### 5. admin Residence 목록처럼 목록에 filter를 최소 하나 요구한다

의도치 않은 넓은 scan을 제한한다. 거부한 이유는 request 목록이 administrator의 작업 큐이고, 가시성이
이미 이를 제한하며, `size`가 100으로 제한되기 때문이다.

## Consequences

Read 슬라이스는 기존 scope 해석과 기존 페이징, page envelope 관례를 재사용하는 좁고 테스트 가능한
가시성 경계를 얻고, error code를 추가하지 않는다. 고정 정렬과 summary projection은 목록 비용을
예측 가능하게 하고 필요한 index를 명시적으로 만든다. `reporter_id`로 찾기, assignment로 찾기, 해석된
dormitory로 찾기이며 각각 `created_at DESC, id DESC` 정렬이다.

가시성 밖 단건 조회에 `404`를 택했다는 것은 같은 request에 대해 read 경로와 command 경로가 다르게
답한다는 뜻이고, 구현은 두 경우를 모두 테스트로 덮어야 한다.

이 결정은 명시적으로 요청된 이후 슬라이스에서 계약을 준수하는 구현을 허가한다. 그 자체로 Java,
Flyway, frozen product 변경을 추가하지 않으며 assignment, lifecycle, visit, attachment, comment
동작은 범위 밖으로 남는다.
