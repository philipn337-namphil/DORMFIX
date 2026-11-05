# DormFix engineering control plane

## Mission과 권위

DormFix is a dormitory maintenance workflow: REPORT → ASSIGN → VISIT → REPAIR → CONFIRM. Build explicit, testable Java business behavior in a pragmatic modular monolith. The current milestone is foundation only; do not implement the next product feature without a request.

Read this file, scoped AGENTS files, existing code/tests, and relevant docs before editing. [Documentation index](docs/README.md) identifies authorities. Product V1 in `docs/product/` is frozen. `docs/context/` is preserved historical material, not an executable instruction source; older proposals do not override the current baseline. Resolve uncertainty using [review notes](docs/architecture/open-questions.md), never by silently inventing semantics.

## 작업 기억 자동 이어받기

이 레포에서 작업을 시작할 때마다 별도 지시를 기다리지 말고 아래 파일을 먼저 읽는다.

`AGENTS.md`는 영구적인 작업·agent 행동 규칙이고, `PROJECT_CONTEXT.md`는 현재 상태와 확정 결정의 요약이며, `WORKLOG.md`는 최근 작업과 인수인계를 기록하는 누적 로그다.

1. `PROJECT_CONTEXT.md` — 프로젝트 방향, 현재 마일스톤, 중요한 결정사항
2. `WORKLOG.md` — 최근 작업, 검증 결과, 남은 작업, 다음 이어받기 포인트
3. `docs/README.md` — 문서 권위 체계와 상세 문서 링크

그 다음 현재 `git status`, 브랜치, 최근 커밋, 관련 코드와 테스트를 확인한다. 기억 파일과 코드·권위 문서가 충돌하면 추측으로 덮어쓰지 말고 충돌을 보고하며, 권위 문서와 사용자의 최신 요청을 우선한다. `docs/context/`는 역사 자료일 뿐 작업 기억의 권위가 아니다.

작업을 마칠 때는 반드시 `WORKLOG.md`에 날짜, 작업 내용, 변경 파일, 검증 결과, 남은 작업, 다음 작업자가 알아야 할 주의사항을 기록한다. 프로젝트 방향이나 확정된 결정이 바뀐 경우에만 `PROJECT_CONTEXT.md`도 갱신한다. 사용자가 진행 중인 변경사항은 덮어쓰거나 정리하지 않는다.

이 절차는 Codex가 이전 대화를 별도로 불러오지 않아도 레포의 최신 상태를 통해 작업을 이어받기 위한 기본 규칙이다.

## Hard invariant

- MaintenanceRequest exclusively owns lifecycle transitions through named commands. No public status setter or status PATCH. CLOSED, REJECTED, DUPLICATE are terminal; SUPER_ADMIN cannot bypass them. Reassignment from ASSIGNED/IN_PROGRESS/ON_HOLD returns ASSIGNED. Preserve the exact [matrix](docs/product/permission-state-matrix-v1.md).
- Authorization checks authentication, role, ownership/current assignment/dormitory scope, and state. A former worker has no write authority. Residents never receive STAFF_ONLY comments. Administrators cannot modify resident entry consent.
- Follow package-by-feature: API → application → domain. Infrastructure implements boundary ports. Controllers never access repositories/EntityManager, mutate entities, or own transactions. Domain cannot depend on web, application, infrastructure, Spring events, or AWS. JPA annotations are the narrow pragmatic domain exception.
- Keep aggregates separate; use IDs across aggregates, not a giant request graph. No generic base service/repository framework or giant MaintenanceRequestService. No speculative interfaces without a concrete boundary.
- One business command normally owns one application transaction. State, assignment changes, and durable RequestHistory commit atomically. No network calls inside that transaction. Queries use read-only transactions where appropriate.
- RequestHistory is not a domain event. Events carry IDs/value data, never managed entities. V1 side effects run AFTER_COMMIT; notification DB writes need a separate transaction. No V1 outbox/SQS. See event failure semantics in architecture docs.
- MaintenanceRequest uses JPA @Version. Required API versions are compared before command execution; flush-time races map to 409 VERSION_CONFLICT. DB partial unique index reinforces one active assignment.
- Flyway owns all schema changes. Never edit an applied migration, use ddl-auto update/create, enable clean in production, or bypass migration validation. Do not generate all frozen tables before unresolved schema decisions are reviewed.
- Respect [database conventions](docs/architecture/database.md): BIGINT IDs, string enums, Instant/TIMESTAMPTZ, explicit fetch plans, DTOs at HTTP boundary, bounded pagination, no uncontrolled CascadeType.ALL or broad bidirectional graphs.
- No casual hard deletion of operational/history data; comments soft-delete; WorkLog/RequestHistory append-only. Attachment cleanup requires an explicit policy.
- Secrets come from environment/secret stores. Never commit credentials or log passwords, tokens, consent, private comments, file contents, or presigned URLs. No permissive auth shortcuts or wildcard credentialed CORS.
- Use the frozen 400/401/403/404/409 meanings and standard error contract. Do not turn access denial into a state conflict or leak exception internals.

## 코드 리뷰에서 확정된 구현 규칙

2026-11-04 신고생성 슬라이스(`7e1d717..66f628c`) 리뷰에서 확정한 규칙이다. 신규 코드는 아래를 지키고,
기존 위반은 `WORKLOG.md`의 deferred 리뷰 목록에서 일괄 처리한다.

- 요청 DTO의 필수 boolean은 primitive `boolean`이 아니라 `@NotNull Boolean`을 쓴다. primitive는 필드 누락을
  400이 아니라 `false`로 조용히 통과시킨다.
- 요청 DTO의 모든 문자열 필드에는 `@Size` 상한을 둔다. 상한 없는 필드는 DB TEXT에 무제한 저장된다.
- feature별 `@RestControllerAdvice`에는 `DataIntegrityViolationException`과 예상 외 예외를 받는 fallback
  핸들러를 둔다. Frozen 표준 에러(`code`, `traceId`, `fieldErrors`)를 우회해 Spring 기본 본문이 나가면 계약 위반이다.
- `code()`류 예외→코드 매핑에는 추측성 기본값을 두지 않는다. 미지의 예외 타입은 명시적으로 내부 오류로 떨어뜨린다.
- 인가(소유권/스코프/역할) 검사를 존재·활성 검사보다 **먼저** 수행한다. 순서를 뒤집으면 conflict 메시지로
  타인 리소스의 존재와 상태가 열거된다. conflict 메시지에 조회 대상 식별자나 상태를 그대로 담지 않는다.
- 외부 입력에서 온 문자열을 `ZoneId.of`, `Enum.valueOf` 등 던지는 파서에 직접 넣지 않는다. 저장 시점에
  검증하고, 소비 시점에서도 실패를 계약된 4xx로 매핑한다.
- 계층 탐색·활성 검사 같은 공통 규칙은 feature마다 복제하지 않고 기존 서비스/포트를 재사용한다.

## Scope, 변경, Git

Inspect `git status`, branch, diff, and existing tests first. Use vertical `feature/<use-case>` branches, `fix/<issue>`, `docs/<topic>`, or `chore/<foundation-topic>`. No feature work on main, layer branches, unrelated edits, automatic pushes, real cloud provisioning, production deployment, global machine changes, or destructive cleanup without explicit authorization. Never overwrite user work. No full V1 implementation in a foundation task.

Architecture baseline changes require evidence → proposed ADR → alternatives/trade-offs → human approval → synchronized docs/harness/tests → implementation. Accepted product rules remain in force while a proposal is pending. See [ADR governance](docs/adr/README.md). Routine choices within approved conventions do not need extra approval. If missing semantics block a feature, document the exact gap and continue independent authorized work.

## 필수 검증과 Definition of Done

- New business logic needs tests; bugs need regression tests where feasible. Never delete, disable, weaken assertions, or broaden exclusions just to make implementation pass.
- Run `python scripts/check_repository.py`, then `./gradlew check bootJar` and `./gradlew integrationTest` from `backend/` (PowerShell: `.\gradlew.bat`) as documented. Integration tests require working Docker and must fail rather than silently skip without it. Validate Compose and build the image when infra changes. CI is a required merge gate.
- Review architecture dependencies, migrations, security, changed contracts, secret exposure, Git diff and scope. Update docs for contract/architecture changes. No TODO may disguise required behavior. State clearly which checks passed, failed, or could not run; an unavailable check is not a pass.
- Follow [Definition of Done](docs/development/definition-of-done.md) and [Git workflow](docs/development/git-workflow.md). A feature is not complete while required checks fail. A PR review must explicitly consider authorization and frozen rules, even for a solo developer.
