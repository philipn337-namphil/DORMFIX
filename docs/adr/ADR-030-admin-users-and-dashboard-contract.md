# ADR-030: 관리자 사용자 조회와 대시보드 요약 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-28

2026-11-28 사람 승인으로 이 계약을 제안대로 수락했다. ADMIN의 resident 조회를 정확 email로 제한하는 것,
응답 field 최소화, rolling 7일 집계를 포함한다.

## Context

Frozen API는 Structure administration 절 끝에서 "Admin group `/admin/...` also includes representative
POST/PATCH residences, GET users and GET dashboard summary"라고만 적는다. `v1-scope.md`도 V1 범위에
"administrator summary query"를 넣었다. 경로, DTO, 권한, 범위는 어디에도 없다.

- **Residence POST/PATCH:** [ADR-018](ADR-018-residence-management-contract.md)이 이미 닫았다. 생성은
  구현되어 있고, V1에는 범용 PATCH가 없으며 종료는 named command다. 이 ADR은 다시 열지 않는다.
- **GET users:** 지금 관리자는 배정할 worker의 id나 입주 등록할 resident의 id를 알아낼 API가 없다.
  배정(`workerId`)과 거주 등록(`residentId`)이 id를 요구하므로 실제로 막혀 있는 공백이다.
- **GET dashboard summary:** 관리자 작업 큐를 한눈에 보는 집계가 없다.

열린 질문은 다음과 같다.

1. 누가 어떤 사용자를 볼 수 있는가. Worker는 dormitory scope가 없고([ADR-021](ADR-021-maintenance-request-assignment-contract.md)),
   새 입주자는 아직 Residence가 없어 scope로 찾을 수 없다.
2. 사용자 응답에 어떤 개인정보(email, phone, student number)를 담는가
3. 대시보드가 무엇을 집계하는가, ADMIN의 범위는 어떻게 정하는가
4. 경로, 필터, 페이징, 오류

## Decision

### GET /admin/users

`GET /admin/users?role=&status=&email=&page=&size=` → `UserPageResponse(content, page, size,
totalElements, totalPages)`. Item은 `UserSummaryResponse(id, name, email, roles, status)`다.

**응답 field 최소화.** `phone`, `studentNumber`, `lastLoginAt`, `createdAt`, password 관련 값은 담지
않는다. 배정과 입주 등록에 필요한 것은 id와 사람을 알아볼 이름·email뿐이다.

**권한:**

| Caller | 허용 범위 |
|---|---|
| `SUPER_ADMIN` | 모든 사용자. `role`, `status`, `email` 필터 모두 선택 |
| `ADMIN` + `role=WORKER` | 모든 worker. Worker는 dormitory scope가 없고 어느 기숙사에든 배정될 수 있으므로 전역으로 본다 |
| `ADMIN` + `role=RESIDENT` | `email` **정확 일치** 조회만. 결과는 0~1건이고 목록 탐색은 없다 |
| `ADMIN`의 그 밖의 요청 | `role` 누락, `role=ADMIN`/`SUPER_ADMIN`, `RESIDENT`에 `email` 누락은 `403 ACCESS_DENIED` |
| `RESIDENT`, `WORKER`만 가진 caller | `403 ACCESS_DENIED` |

`ADMIN`은 scope가 없어도 worker 목록과 resident 정확 조회를 쓸 수 있다. 사용자는 dormitory에 속하지
않으므로 scope로 거를 대상이 없고, scope 검사는 그 뒤의 배정·입주 등록 command가 이미 한다.

Resident를 정확 email 조회로만 여는 이유: 새 입주자는 Residence가 없어 scope로 찾을 수 없고, 그렇다고
`ADMIN`에게 전체 resident 목록을 열면 다른 기숙사 입주자의 이름과 email이 그대로 열거된다. 입주자가
알려준 email로 한 명을 찾는 것이 등록 업무에 충분하다.

**필터와 정렬:**
- `role`은 단일 값, `status`는 `ACTIVE`/`SUSPENDED`/`WITHDRAWN` 중 단일 값이다. 해석할 수 없으면
  `400 VALIDATION_FAILED`.
- `email`은 trim + lowercase로 정규화한 뒤 정확히 비교한다(signup 정규화와 같다). 부분 일치는 없다.
- 정렬은 `name ASC, id ASC` 고정. `page` 0부터, `size` `1..100` 기본 `20`.
- 한 사용자가 여러 role을 가지면 `roles`에 모두 담는다. `role` 필터는 그 role을 가진 사용자를 고른다.

### GET /admin/dashboard/summary

`GET /admin/dashboard/summary?dormitoryId=` → `DashboardSummaryResponse(dormitoryIds, statusCounts,
activePriorityCounts, createdLast7Days, closedLast7Days, generatedAt)`.

- `statusCounts`: 8개 status 전부를 key로, 없는 status는 `0`.
- `activePriorityCounts`: terminal이 아닌(`CLOSED`/`REJECTED`/`DUPLICATE` 제외) request의 priority별
  개수. 4개 priority 전부를 key로, 없는 값은 `0`.
- `createdLast7Days`, `closedLast7Days`: `generatedAt` 기준 직전 7×24시간에 생성·종료(`closed_at`)된
  개수. 기숙사별 timezone의 "오늘"을 따지지 않는 rolling 구간이다.
- `dormitoryIds`: 집계에 포함된 dormitory id 목록(정렬). Client가 범위를 확인할 수 있게 한다.

**범위:**
- `SUPER_ADMIN`: `dormitoryId`가 없으면 전체(`dormitoryIds`는 빈 배열이 아니라 존재하는 모든
  dormitory), 있으면 그 dormitory 하나. 없는 dormitory id는 `404 DORMITORY_NOT_FOUND`(기존 code).
- `ADMIN`: `dormitoryId`가 없으면 scope의 모든 dormitory 합계, 있으면 scope 안이어야 하고 밖이면
  `403 ACCESS_DENIED`. Scope가 없는 `ADMIN`은 `403 ACCESS_DENIED`(request 목록과 같다).
- 그 밖의 role은 `403 ACCESS_DENIED`.
- 집계는 request의 space → building → dormitory 경로로 범위를 정한다. 비활성화된 dormitory·건물의
  request도 센다(이력이 사라지면 안 되므로).

**성능:** read-only transaction에서 집계 query 몇 개로 끝낸다. 캐시와 materialized view는 V1에 두지
않는다. 개수만 반환하고 request 목록은 반환하지 않는다(목록은 기존 request 목록 API).

### 공통

- 경로는 `/api/v1/admin/**`이며 이미 인증 필요 경로다.
- 새 error code는 없다. `403 ACCESS_DENIED`, `400 VALIDATION_FAILED`는 frozen code이고,
  `404 DORMITORY_NOT_FOUND`는 구조 API가 이미 쓰는 code라 error contract의 대표 목록에 함께 적는다.
- Schema 변경은 없다. 필요한 index는 기존 것으로 충분한지 구현 때 확인하고, 부족하면 새 migration으로만
  추가한다.
- 배치: 사용자 조회는 `identity` feature, 대시보드는 `maintenance` feature에 둔다.

## Alternatives

1. **ADMIN에게 모든 resident 목록을 연다.** 구현은 단순하지만 다른 기숙사 입주자의 개인정보가 열거된다.
2. **ADMIN의 resident 조회를 현재 거주 scope로 제한한다.** 새 입주자(Residence 없음)를 찾을 수 없어
   입주 등록 업무를 풀지 못한다. 정확 email 조회가 그 경우를 덮는다.
3. **Worker에게 dormitory scope를 새로 만든다.** 배정 범위까지 바뀌는 큰 변경이고 ADR-021과 ADR-014를
   다시 열어야 한다. V1 범위를 넘는다.
4. **응답에 phone·student number를 담는다.** 연락에는 편하지만 이 조회의 목적(id 찾기)에 필요 없다.
5. **대시보드에 SLA·지연 건수를 넣는다.** "지연"의 기준이 Frozen에 없어 새 제품 규칙을 만드는 일이다.
6. **기숙사 timezone 기준 "오늘/이번 주" 집계.** 여러 기숙사를 합칠 때 기준이 갈린다. Rolling 7일로 단순화한다.

## Consequences

- 승인되면 Frozen API spec(두 경로의 DTO, 권한, 오류), permission matrix(행 추가)를 동기화하고 baseline
  hash를 갱신한다. ERD와 schema는 바꾸지 않는다.
- 사용자 조회는 role별 경로를 별도 query method로 둔다. `ADMIN`의 resident 경로는 정확 email method만
  호출할 수 있게 해 목록 탐색이 구조적으로 불가능하게 한다.
- 이 슬라이스로 Phase 5의 frozen 표면이 모두 구현된다. 남는 V1 범위는 실제 S3 adapter와 배포다.
