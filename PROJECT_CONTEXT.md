# DormFix 프로젝트 기억

이 파일은 다음 작업자가 현재 프로젝트 상태와 확정 결정을 빠르게 이어받기 위한 요약이다. 영구 규칙은 `AGENTS.md`, 제품·아키텍처 권위는 `docs/README.md`에 연결된 문서를 따른다.

## 프로젝트 목적

DormFix는 기숙사 시설 문제를 `REPORT → ASSIGN → VISIT → REPAIR → CONFIRM` 흐름으로 관리하는 서비스다. 신고, 담당자 배정, 방문·수리 기록, 확인과 감사 이력을 명확하고 테스트 가능하게 제공한다.

## 현재 상태

2026-11-27 기준이다. 세부 경과는 `WORKLOG.md`의 최신 항목을 따른다.

- **Phase 3 MaintenanceRequest workflow의 계약 표면이 완성되었다.** 신고 생성, 조회, 배정,
  lifecycle 전이 8개, visit, work log, metadata PATCH, comment, attachment가 구현되었고 각각 Accepted
  ADR(ADR-019~027)과 Frozen 계약 동기화를 갖는다. 2026-11-27 history 조회(ADR-028)와 notification·domain event(ADR-029)를 추가했다.
- Flyway migration은 V1~V14이다. 적용된 migration은 수정하지 않으며, RequestHistory event type을
  늘리는 슬라이스는 `ck_request_history_event_type`을 새 migration에서 drop 후 재생성한다.
- `main`에는 PR #23(attachment)까지 병합되어 있다.
- **Attachment는 실제 object storage에 연결되어 있지 않다.** Storage는 `AttachmentStorage` port
  뒤에 있고 기본 adapter는 닫힌 채로 실패한다. 실행 중인 application에서 presign, 등록,
  download-url은 `503 STORAGE_UNAVAILABLE`을 답한다. 계약, 인가, 검증, metadata 저장은 구현되고
  메모리 fake로 테스트된다. 실제 S3 adapter와 고아 object 정리 작업은 별도 슬라이스다.
- 아직 구현되지 않은 frozen 범위: admin users·dashboard 조회(계약 미정), 실제 S3 adapter,
  배포(EC2/RDS/S3). 다음 제품 기능은 사용자의 명시적인 요청 없이는 구현하지 않는다.
- 2026-11-27 사용자 결정: 남은 V1 기능을 마저 개발한 뒤 AWS에 전통 방식(EC2)으로 배포하고, 이어서
  서버리스로 마이그레이션한다. 순서와 범위는 `docs/development/roadmap.md`의 Phase 5~11을 따른다.
  서버리스 전환은 착수 전에 Proposed ADR과 승인이 필요하다.
- 전체 코드리뷰는 위 작업 뒤로 미뤘다. 신고 생성 슬라이스의 deferred 지적과 리뷰 때 정리할 항목은
  `WORKLOG.md`에 기록되어 있다.
- Product V1 계약은 `docs/product/`에 frozen 상태로 관리한다. 계약 공백은 증거 → Proposed ADR →
  사람 승인 → Frozen 동기화와 baseline hash 갱신 → 구현 순서로만 닫는다.
- ADR과 PR 본문, 작업 기록은 한국어로 쓴다. Frozen 계약 문서 `api-spec-v1.md`와 `erd-v1.md`,
  그리고 커밋 메시지는 영어를 유지한다.
- 아키텍처는 명시적이고 테스트 가능한 Java modular monolith를 따른다.
- 백엔드는 package-by-feature 구조의 API → application → domain 흐름을 따른다.
- 인프라는 boundary port를 구현하며, Controller가 repository나 EntityManager에 직접 접근하거나 transaction을 소유하지 않는다.
- 데이터베이스 변경은 Flyway migration으로만 관리한다.

## 현재 확정 결정

- Authentication Phase 1의 signup, login, RS256 bearer authentication, `/api/v1/me`, refresh rotation, logout vertical slice가 구현·검증되었다.
- Access token은 `Authorization: Bearer <JWT>`로 전달하고, JWT의 signature/issuer/audience/`exp`/`nbf`/algorithm allow-list를 검증한다.
- Refresh token은 14일 opaque token이며 raw 값은 저장하지 않고 SHA-256 hash만 `refresh_token_sessions`에 저장한다. Rotation은 기존 row revoke 후 새 row 생성이며 token family/`replaced_by`는 V1에서 사용하지 않는다.
- Signup email은 trim + lowercase normalize하고, role/status는 서버가 `RESIDENT`/`ACTIVE`로 부여한다. Dormitory scope 저장 방식은 이번 Phase에서 미결정이다.
- 상세 결정은 `docs/architecture/security.md`, `docs/adr/ADR-013-refresh-token-storage.md`, `docs/architecture/open-questions.md`를 따른다.

## Phase 3에서 확정된 결정 요약

상세와 대안은 각 ADR을 따른다. 여기서는 다음 작업자가 반드시 알아야 할 것만 적는다.

- 조회(ADR-020): 가시성은 role별 기준(reporter, assignment row, dormitory scope)으로 정한다.
  가시성 밖 단건 조회는 `404`이고 command는 `403`을 유지한다.
- 배정(ADR-021): assignment table이 단일 진실 원천이며 denormalized `assignee_id`가 없다. 중복
  assignee와 active-assignment 경합은 `409 REQUEST_ALREADY_ASSIGNED`이고 `VERSION_CONFLICT`로 접지
  않는다. 재배정은 종료 UPDATE를 먼저 flush한 뒤 새 행을 INSERT한다.
- Lifecycle(ADR-022): `status_reason`은 현재 status의 투영이다. Terminal 전이는 active assignment를
  종료하고 `resolve`는 종료하지 않는다. `WORKER_NOT_ASSIGNED`와 `ACCESS_DENIED`는 뜻이 다르다.
- Visit(ADR-023): visit의 worker는 서버가 active assignee에서 유도한다. Request당 열린 visit은
  하나다. Resident는 `note`를 받지 않는다.
- WorkLog(ADR-024): current active assignee만 작성하고 읽기는 staff 전용이다. Request를 수정하지
  않고 RequestHistory를 남기지 않는다.
- Metadata PATCH(ADR-025): resident와 administrator의 field 집합은 교집합이 없다. `spaceId`는
  PATCH로 바꾸지 않는다. Category 변경은 priority를 재설정하지 않는다.
- Comment(ADR-026): 권한은 role이 아니라 request에 대한 관계로 정한다. Resident는 `STAFF_ONLY`의
  row, 개수, 삭제 응답 어느 것으로도 그 존재를 알 수 없다.
- History 조회(ADR-028): 가시성은 detail과 같고 밖은 `404`다. Resident view는 `COMMENT_DELETED` 행을
  받지 않는다. Raw metadata 대신 typed field로 투영한다. Lifecycle reason은 history `metadata.reason`에
  JSON serializer로 기록한다(이전에는 누락되던 공백).
- Notification(ADR-029): command commit 뒤 AFTER_COMMIT listener가 REQUIRES_NEW로 기록한다.
  best-effort·최대 한 번이며 실패가 command를 되돌리지 않는다. actor 제외, STAFF_ONLY comment는
  reporter에게 알리지 않는다. 문구는 서버 고정 문구다. Event는 `maintenance.application.event`의
  `MaintenanceEvent`이고 notification이 maintenance에 의존한다(반대 방향 금지).
- Attachment(ADR-027): object key는 서버가 만들고 request와 uploader를 접두에 담는다. 등록은
  storage가 본 크기와 type을 기록한다. 한도는 설정이다. Application은 object를 삭제하지 않는다.

## 핵심 규칙 위치

MaintenanceRequest lifecycle, authorization, transaction, event, JPA, retention, secret 보호 규칙은 `AGENTS.md`와 해당 `docs/architecture/` 권위 문서를 따른다. 이 파일에서 영구 규칙을 복제해 재정의하지 않는다.

## 권위 문서 탐색 순서

1. `docs/product/`의 frozen 계약
2. `docs/architecture/`의 승인된 설계와 `open-questions.md`
3. `docs/development/`의 검증·작업 규칙
4. 이 파일과 `WORKLOG.md`의 현재 작업 요약

문서 전체 목록은 [`docs/README.md`](docs/README.md)를 확인한다.

## 다음 작업 시작 시 확인할 것

- `WORKLOG.md`의 최신 기록과 남은 작업
- 현재 브랜치와 `git status`
- 이번 요청과 직접 관련된 Product/Architecture 문서
- 관련 코드와 테스트
- 작업 종료 전 필수 검증 결과와 변경 범위
-
## 2026-09-23 implementation update

- ADR-016 Dormitory/Building/Space Admin write APIs are implemented under `/api/v1/admin`.
- Create, metadata PATCH, explicit deactivate, and explicit reactivate commands are SUPER_ADMIN-only.
- Child creation/reactivation below an inactive parent returns 409 `INACTIVE_PARENT`; PATCH DTOs reject unknown fields such as `active`.
- Facility and MaintenanceCategory Admin write APIs remain intentionally unimplemented.
