# DormFix 프로젝트 기억

이 파일은 다음 작업자가 현재 프로젝트 상태와 확정 결정을 빠르게 이어받기 위한 요약이다. 영구 규칙은 `AGENTS.md`, 제품·아키텍처 권위는 `docs/README.md`에 연결된 문서를 따른다.

## 프로젝트 목적

DormFix는 기숙사 시설 문제를 `REPORT → ASSIGN → VISIT → REPAIR → CONFIRM` 흐름으로 관리하는 서비스다. 신고, 담당자 배정, 방문·수리 기록, 확인과 감사 이력을 명확하고 테스트 가능하게 제공한다.

## 현재 상태

- 현재 브랜치는 `main`이다.
- 현재 Phase는 foundation 기준 위에 Authentication Phase 1을 완료한 상태다.
- Authentication feature commit `2984092b48ff4d7cb1cffa4507492b2777a5c79c`는 `origin/main`과 `origin/feature/authentication`에 반영되어 있다.
- 다음 제품 기능은 사용자의 명시적인 요청 없이는 구현하지 않는다.
- Product V1 계약은 `docs/product/`에 frozen 상태로 관리한다.
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
