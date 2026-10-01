# DormFix 작업 로그

## 2026-10-01 - Phase 2 final quality gate passed

- Final Residence pageable-query review fixed PostgreSQL native SQL date casting and prevented
  Spring Data from appending Java property sort names to the native `start_date`/`id` ordering.
  SecurityBoundaryTest now supplies the new query-service dependency in its MVC slice.
- Verification passed: `compileJava`, `test`, targeted Residence Testcontainers integration,
  `check bootJar`, full `integrationTest` (14 suites, 40 tests, 0 failures, 0 errors, 0 skipped),
  `python scripts/check_repository.py`, and `git diff --check`.
- No commits, pushes, resets, stashes, checkouts, or build-result deletion were performed.
- Phase 3 handoff: preserve the approved Phase 2 structure/Residence contracts, scope boundary,
  explicit command endpoints, and Residence exclusion constraint; do not expand them while adding
  MaintenanceRequest workflow behavior.

## 2026-10-01 - Phase 2 Residence contract alignment and audit

- Aligned Accepted ADR-018 and frozen Residence contract with current-only creation:
  `CreateResidenceRequest` now has only `residentId`, `roomSpaceId`, and `startDate`; closed
  historical creation and create-time `endDate` are not a V1 path. The ADR now records allowed
  two-command same-day end-then-create movement.
- Added the admin filtered page endpoint and RESIDENT self page endpoint. Both use stable
  `startDate DESC, id DESC` ordering, page/size bounds, per-room Dormitory scope filtering, and
  the required admin filter rule. Scope-less ADMIN and explicit out-of-scope room filters are 403.
- Preserved V5 PostgreSQL `btree_gist` exclusion protection and added named-constraint mapping to
  `409 RESIDENCE_PERIOD_OVERLAP`. The Residence Testcontainers test now also directly exercises
  the database exclusion constraint.
- Synchronized `erd-v1`, `api-spec-v1`, permission matrix, database/security architecture notes,
  open-question register, and frozen baseline hashes. `python scripts/check_repository.py` and
  `git diff --check` passed.
- Verification: `gradlew test --no-daemon --console=plain` and `gradlew check bootJar --no-daemon
  --console=plain` passed using local Temurin 21. Targeted `ResidenceIntegrationTest` passed
  against PostgreSQL Testcontainers (1 test, 0 failures/errors). Full `integrationTest` was
  attempted; its first attempt failed because an already-running DormFix Gradle worker held
  `build/test-results/integrationTest/binary/output.bin`. No files were removed; `gradlew --stop`
  stopped the proven DormFix daemon. The subsequent full run exceeded the tool execution window,
  so full-suite completion remains unverified.
- Remaining: split and expand Residence integration coverage for all role, pagination, invalid
  input, inactive hierarchy, same-day move, and multi-resident scenarios before declaring Phase 2
  complete. Re-run the full `integrationTest` to completion after ensuring no prior Gradle worker
  is active. Do not update PROJECT_CONTEXT to Phase 2 complete until that gate and coverage pass.

## 2026-09-30 - Phase 2 contract proposals; implementation held

- Added proposed ADR-017 for MaintenanceCategory reactivation and proposed ADR-018 for Residence
  management. They contain the exact proposed API, role/scope authorization, state/period rules,
  error meanings, alternatives, and persistence direction.
- Updated `docs/architecture/open-questions.md` to distinguish these approval-gated proposals from
  the existing Frozen contract. Updated the ADR index to label ADR-017/018 as Proposed.
- No Java code, Flyway migration, test, Frozen product document, frozen hash, commit, push, or
  branch state was changed in this design pass.
- Human approval is required before contract synchronization or implementation. In particular,
  approve the Residence half-open date model, current-ending behavior, no-future-reservation
  policy, move flow, role matrix, and PostgreSQL exclusion-constraint direction; also approve the
  explicit Category reactivate command.
- Verification: document-only `python scripts/check_repository.py` and `git diff --check` passed.
  Do not treat the existing implementation as contract authority.

## 2026-09-30 - Phase 2 structure-management completion pass

- Completed Facility administration vertical slice: scoped ADMIN/SUPER_ADMIN create and metadata PATCH,
  plus explicit out-of-service, reactivate, and retire commands. Facility PATCH cannot bind status and
  RETIRED remains terminal.
- Completed MaintenanceCategory administration vertical slice: SUPER_ADMIN create, allow-listed metadata
  PATCH, explicit deactivate/reactivate, immutable create-only parentId, and inactive-parent protection
  for child creation/reactivation.
- Preserved V3/V4 migrations, existing structure read APIs, and Dormitory/Building/Space admin APIs.
  Security now authenticates `/api/v1/admin/**` and leaves the Facility scope decision in the application
  command service; the existing SUPER_ADMIN-only commands retain their application checks.
- Added PostgreSQL/Testcontainers API coverage for Facility scope/lifecycle, Category authorization,
  parentId patch rejection, and inactive parent conflicts. The suite also exercises the existing V3/V4
  migration path.
- Changed areas: `backend/src/main/java/com/dormfix/catalog/**`, Facility/Category ports and domain models,
  `SecurityConfiguration`, structure error advice, security boundary test, and catalog integration test.
- Verification: `backend` `gradlew test`, `gradlew check bootJar`, and `gradlew integrationTest` passed
  with the repository JDK 21 and PostgreSQL Testcontainers; see current handoff for final guard results.
- Remaining decision: Residence cannot be implemented safely. Frozen ERD leaves date boundary/overlap open,
  while frozen API lacks Residence paths, DTOs, authorization, close/replace history semantics, and error
  mapping. Recorded in `docs/architecture/open-questions.md`. Category reactivate is required by this
  Phase request and parent-reactivation policy but absent from the frozen API table; recorded for approved
  contract synchronization.
- Phase 3 handoff: do not assume Residence semantics or silently promote category reactivate into the
  frozen API contract. Maintenance Request work must consume Structure IDs and preserve the aggregate,
  authorization, and lifecycle invariants in `AGENTS.md`.

## 2026-09-20 - Admin structure API contract frozen

- 사용자가 승인한 Admin API 계약을 ADR-016으로 확정했다.
- API inventory에 Dormitory/Building/Space/Facility/MaintenanceCategory 관리 path와 allow-listed DTO를 기록했다.
- Dormitory name은 V1 unique가 아니며 duplicate-name 409를 제거했다.
- Facility command path를 `/out-of-service`, `/reactivate`, `/retire`로 확정하고 `RETIRED` terminal 전이를 문서화했다.
- MaintenanceCategory `parentId`는 create-only로 확정했으며 category move API는 V1에서 제공하지 않는다.
- API, permission matrix, security guidance, open-question register, ADR index 및 PROJECT_CONTEXT를 동기화했다.
- 변경 파일: `docs/product/api-spec-v1.md`, `docs/product/permission-state-matrix-v1.md`, `docs/adr/ADR-015-structure-management-authorization.md`, `docs/adr/ADR-016-admin-structure-api-contract.md`, `docs/adr/README.md`, `docs/architecture/security.md`, `docs/architecture/open-questions.md`, `PROJECT_CONTEXT.md`, `WORKLOG.md`, `docs/product/frozen-baseline.sha256.json`
- Java Controller/Application Service는 구현하지 않았다.
- 검증: `python scripts/check_repository.py` 통과; `git diff --check` 통과. Java/Gradle/Docker 검증은 요청 범위 밖이라 실행하지 않았다.

이 파일은 작업 간 인수인계를 위한 누적 로그다. 최신 기록을 위에 추가하고, 이미 기록된 내용을 임의로 지우지 않는다.

## 현재 handoff — 2026-09-14

- 현재 브랜치: `main`
- Authentication Phase 1 commit `2984092b48ff4d7cb1cffa4507492b2777a5c79c`를 생성하고 `origin/feature/authentication`에 push한 뒤 `main`에 fast-forward merge했다.
- `main`도 같은 commit으로 `origin/main`에 push했다.
- repository guard, Gradle `test`, Checkstyle, ArchUnit, PostgreSQL Testcontainers `integrationTest`, `check bootJar` 검증을 통과했다.
- 현재 남은 working-tree 변경은 이 handoff 파일을 commit하기 전의 `AGENTS.md`, `PROJECT_CONTEXT.md`, `WORKLOG.md`뿐이다.
- 다음 작업은 이 파일과 `AGENTS.md`, `docs/README.md`를 먼저 읽고, 사용자가 요청한 범위에서만 진행한다.

이 아래의 과거 기록은 각 기록 당시의 상태를 보존한 historical log다. 현재 남은 작업과 브랜치 상태는 이 handoff와 최신 날짜 항목을 기준으로 확인한다.

## 2026-09-13 — Login vertical slice와 JWT access-token issuance 구현

### 수행한 작업

- `POST /api/v1/auth/login`을 공개 endpoint로 추가하고 email trim/lowercase normalize, 사용자 조회, password 검증, ACTIVE 상태 검증을 구현했다.
- 성공한 login transaction에서 `last_login_at`과 `updated_at`을 같은 초 단위 시각으로 갱신한다.
- application의 `AccessTokenIssuer` port와 infrastructure의 Spring Security JOSE/Nimbus RS256 adapter를 추가했다.
- JWT에 key ID, issuer, audience, subject user ID, roles, `iat`, `nbf`, 30분 `exp`, `jti`를 기록한다.
- RSA X.509 DER public key와 PKCS#8 DER private key의 Base64 값, issuer, audience, key ID를 환경설정으로만 받도록 구성했다.
- 존재하지 않는 email, 잘못된 password, SUSPENDED/WITHDRAWN 상태를 동일한 `401 INVALID_CREDENTIALS`로 처리하여 계정 상태를 노출하지 않는다.
- login request/response DTO, API/application/infrastructure 테스트와 실제 RSA signature 검증을 포함한 PostgreSQL Testcontainers 테스트를 추가했다.

### 변경한 파일

- `backend/build.gradle`
- `backend/src/main/java/com/dormfix/identity/api/LoginController.java`
- `backend/src/main/java/com/dormfix/identity/api/LoginExceptionHandler.java`
- `backend/src/main/java/com/dormfix/identity/api/LoginRequest.java`
- `backend/src/main/java/com/dormfix/identity/api/LoginResponse.java`
- `backend/src/main/java/com/dormfix/identity/application/AccessTokenIssuer.java`
- `backend/src/main/java/com/dormfix/identity/application/IssuedAccessToken.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginCommand.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginRejectedException.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginResult.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginService.java`
- `backend/src/main/java/com/dormfix/identity/domain/User.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/JwtAccessTokenIssuer.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/JwtConfiguration.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/JwtProperties.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/SpringDataUserRepository.java`
- `backend/src/main/java/com/dormfix/platform/config/SecurityConfiguration.java`
- `backend/src/test/java/com/dormfix/identity/api/LoginControllerTest.java`
- `backend/src/test/java/com/dormfix/identity/application/LoginServiceTest.java`
- `backend/src/integrationTest/java/com/dormfix/identity/api/LoginIntegrationTest.java`
- `backend/src/integrationTest/java/com/dormfix/test/TestJwtKeys.java`
- 기존 full-context/security 테스트와 identity package 문서를 JWT 설정에 맞춰 갱신했다.
- `.env.example`, `compose.yml`, `infra/production/app.env.example`
- `docs/architecture/security.md`, `docs/development/local-development.md`

### 검증한 내용

- `python scripts/check_repository.py`: 통과
- `backend/gradlew check bootJar`: 통과
- `backend/gradlew integrationTest --rerun-tasks`: PostgreSQL 17.6 Testcontainers에서 13개 통과, 실패/skip 0
- RS256 signature, `kid`, issuer, audience, subject, roles, `iat`/`nbf`, 30분 expiry와 DB `last_login_at` 갱신을 검증했다.
- 첫 integration 실행에서 JWT NumericDate의 초 단위와 response Instant의 nanosecond 차이로 1개 assertion이 실패했다. Login command 시각을 초 단위로 정규화한 뒤 전체 재실행이 통과했다.
- `docker compose config --quiet`: 최종 통과. 첫 검증 helper는 host PowerShell의 RSA DER export API 부재로 실패했으며 비밀이 아닌 Base64 placeholder로 Compose interpolation만 재검증했다.
- `docker build -t dormfix:login-validation backend`: 통과하며 이미지 내부 `check bootJar`도 통과했다.
- private key나 실제 credential이 저장소에 기록되지 않았음을 확인했다.

### 남은 작업

- JWT bearer authentication filter/resource-server verification
- `/api/v1/me`
- refresh-token 발급·rotation endpoint와 login response의 refresh token
- logout/revocation endpoint

### 다음 작업자가 알아야 할 주의사항

- 현재 login response는 이번 요청 범위에 따라 access token만 반환한다. 확정 정책의 refresh token 반환은 refresh slice에서 원자적으로 완성해야 한다.
- app 실행에는 `DORMFIX_SECURITY_JWT_ISSUER`, `DORMFIX_SECURITY_JWT_AUDIENCE`, `DORMFIX_SECURITY_JWT_KEY_ID`, public/private Base64 DER key 설정이 필요하다.
- bearer verification은 아직 연결하지 않았으므로 signup/login 외 business traffic은 계속 deny한다.
- signing key와 token/password는 로그, error, fixture 파일에 남기지 않는다.

## 2026-09-13 — 작업 기억 자동 이어받기 체계 추가

### 수행한 작업

- 이전 작업을 별도 명령 없이 이어받을 수 있도록 프로젝트 기억 문서 체계를 추가했다.
- `AGENTS.md`에 작업 시작 시 자동 확인하고 종료 시 로그를 갱신하는 규칙을 추가했다.

### 변경한 파일

- `AGENTS.md`
- `PROJECT_CONTEXT.md`
- `WORKLOG.md`

### 검증한 내용

- 현재 브랜치: `feature/authentication`
- 기존 사용자 변경사항 3개가 있었으며 보존했다.
- 최근 커밋과 `docs/README.md`의 문서 권위 체계를 확인했다.

### 남은 작업

- 없음. 이후 모든 작업에서 이 파일의 최신 기록을 먼저 확인한다.

### 다음 작업자가 알아야 할 주의사항

- 기존 변경사항을 덮어쓰지 않는다.
- Product V1 frozen 문서와 `docs/architecture/open-questions.md`를 우선한다.
- 작업 종료 시 이 로그에 새 날짜별 항목을 추가한다.
-
## 2026-09-14 - Login slice final verification follow-up

- Added fail-fast validation that JWT public/private RSA keys use the same modulus.
- Re-ran repository checks, Gradle check/bootJar, and PostgreSQL Testcontainers integration tests; all passed.
- Rebuilt the Docker image and revalidated Compose configuration successfully.
- Remaining scope is unchanged: bearer verification/filter, /me, refresh/rotation, and logout are not implemented.
- 2026-09-14 follow-up: implemented RS256 Bearer validation and `GET /api/v1/me`; full Docker `check bootJar` passed, and all 15 PostgreSQL Testcontainers integration tests passed.
- Bearer integration coverage includes missing token, expired token, future `nbf`, invalid signature, issuer and audience failures returning 401. Refresh/logout remain deferred.
- 2026-09-14 refresh follow-up: login now issues a 32-byte URL-safe opaque refresh token and stores only its SHA-256 hash with a 14-day expiry.
- Added `/api/v1/auth/refresh` rotation: the existing session is revoked before a replacement session is saved; revoked, expired, reused, and non-ACTIVE-user tokens are rejected with 401. Raw refresh tokens are not logged or persisted.
- Docker `check bootJar` passed and all 17 PostgreSQL Testcontainers integration tests passed. Logout remains deferred.
- 2026-09-14 logout follow-up: added `POST /api/v1/auth/logout`; it hashes the supplied refresh token, revokes the matching session, and returns 204. Access tokens remain stateless with no blacklist.
- Added regression coverage proving logout makes the same refresh token fail at `/api/v1/auth/refresh`; raw refresh tokens are not logged or persisted. Full PostgreSQL Testcontainers integration suite passed: 18 tests.
- 2026-09-14 path-only cleanup: moved `TokenPairResult.java` from `identityapplication` to `identity/application`; file bytes and package declaration were preserved.
- 2026-09-16: reviewed authoritative Dormitory/Building/Space/Facility/MaintenanceCategory ERD, API, database, permission, and scope documents for `feature/manage-dormitory-structure`; no code or migration was added. Management write DTO/path details, nullability/defaults, FK delete actions, and dormitory scope remain unresolved where documented.
- 2026-09-16: confirmed V1 physical rules for the dormitory structure and added `V3__dormitory_structure.sql` for Dormitory → Building → Space → Facility plus MaintenanceCategory self-reference. PostgreSQL 17.6 SQL application, repository guard, and diff check passed; Java code was not added.
- 2026-09-16 Phase 2 handoff: implemented only JPA domain models and string enums for Dormitory, Building, Space, Facility, and MaintenanceCategory under `location.domain`/`catalog.domain`; all aggregate FKs remain scalar `Long` IDs with no bidirectional associations, CascadeType.ALL, or eager loading. `test checkstyleMain checkstyleTest` passed in Docker JDK. `integrationTest` reached the existing `FoundationIntegrationTest` and failed because its migration version assertion still expects `2` after V3; no application failure was established before the run was interrupted. Next step is to update only V3-related test assertions and rerun PostgreSQL Testcontainers validation. No Repository/API/Application Service was added.
- 2026-09-16 Phase 2 continuation: updated V3-related FoundationIntegrationTest assertions and added PostgreSQL JPA mapping coverage. Full `integrationTest` and Gradle `check` passed in Docker JDK; repository guard and diff check passed. No Repository/API/Application Service was added.
- 2026-09-16: implemented the six frozen Dormitory Structure GET endpoints with read-only application services and explicit response DTOs; entities are not returned at the HTTP boundary. Added authenticated access, stable 404 error responses, and PostgreSQL Testcontainers API coverage. Gradle `check`, full `integrationTest`, repository guard, and diff check passed. Admin create/update/deactivate APIs remain unimplemented.
- 2026-09-18: accepted ADR-014 and resolved Q02 for V1 administrator scope storage using `admin_dormitory_scopes`; added immutable `V4__admin_dormitory_scopes.sql` with composite PK, explicit RESTRICT FKs, and reverse lookup index. Added PostgreSQL Testcontainers coverage for schema objects, duplicate PK rejection, and orphan FK rejection. No Java Entity/Repository/API was added.
- 2026-09-18 verification: Docker-backed PostgreSQL `integrationTest` passed. Initial attempts without a compatible JAVA_HOME were environment failures only; rerun with temporary JDK 21 completed successfully.
- 2026-09-18: implemented scalar-ID JPA model and minimal Repository adapter for `admin_dormitory_scopes` using `AdminDormitoryScopeId`/`@EmbeddedId`; added PostgreSQL Testcontainers coverage for save/query/delete and composite-PK duplicate rejection. `checkstyleMain`, `checkstyleTest`, targeted scope integrationTest, and full integrationTest passed. No scope authorization service or Admin API was added.
- 2026-09-18: added `DormitoryScopeAuthorizationService` for SUPER_ADMIN global access and ADMIN explicit-scope access; scope-less ADMIN, RESIDENT, and WORKER are denied via an `AccessDeniedException` with the existing "Access is denied." contract. Added role/scope unit and PostgreSQL integration coverage. Admin CRUD/API remains unimplemented.
- 2026-09-18: accepted ADR-015 and synchronized the Frozen permission matrix, API inventory, security guidance, and open-question register. Confirmed SUPER_ADMIN-only Dormitory/Building/Space management, scoped ADMIN/SUPER_ADMIN Facility management, SUPER_ADMIN-only global MaintenanceCategory, no parent deactivation propagation, inactive-parent creation/reactivation blocking, and no hard delete. Updated the canonical frozen baseline hashes; no Java or migration changes.
- 2026-09-18: implemented `DormitoryScopeResolutionService` for Space → Building → Dormitory and Facility → Space → Building → Dormitory resolution, delegating resolved IDs to existing scope authorization. Added Facility ID lookup port/adapter, `FACILITY_NOT_FOUND` mapping, unit coverage for path/delegation/404, and PostgreSQL integration coverage for 403/404. Admin Controller/CRUD remains unimplemented.
- 2026-09-18 verification: resolved the initial ArchUnit catalog/location cycle by introducing the narrow `FacilityScopeLookup` application port and catalog infrastructure adapter. Full unit tests, ArchitectureTest, Checkstyle, targeted and full PostgreSQL integrationTest passed.
- 2026-09-23: implemented ADR-016 Admin write APIs for Dormitory, Building, and Space only. Added create, metadata PATCH, explicit deactivate, and explicit reactivate commands; all require SUPER_ADMIN at the Security and application boundaries. PATCH DTOs are allow-listed and reject direct `active` changes. Creation/reactivation below inactive parents returns 409 `INACTIVE_PARENT`; parent deactivation does not cascade. Added unit tests and PostgreSQL Testcontainers API coverage for authorization, lifecycle commands, active-field protection, and inactive-parent conflicts. Unit/API compilation, ArchitectureTest, and Checkstyle passed. PostgreSQL integrationTest compiled but could not run because Docker daemon was unavailable in the environment.
