# DormFix 작업 로그

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
