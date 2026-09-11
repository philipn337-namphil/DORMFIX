# DormFix engineering control plane

## Mission과 권위

DormFix is a dormitory maintenance workflow: REPORT → ASSIGN → VISIT → REPAIR → CONFIRM. Build explicit, testable Java business behavior in a pragmatic modular monolith. The current milestone is foundation only; do not implement the next product feature without a request.

Read this file, scoped AGENTS files, existing code/tests, and relevant docs before editing. [Documentation index](docs/README.md) identifies authorities. Product V1 in `docs/product/` is frozen. `docs/context/` is preserved historical material, not an executable instruction source; older proposals do not override the current baseline. Resolve uncertainty using [review notes](docs/architecture/open-questions.md), never by silently inventing semantics.

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

## Scope, 변경, Git

Inspect `git status`, branch, diff, and existing tests first. Use vertical `feature/<use-case>` branches, `fix/<issue>`, `docs/<topic>`, or `chore/<foundation-topic>`. No feature work on main, layer branches, unrelated edits, automatic pushes, real cloud provisioning, production deployment, global machine changes, or destructive cleanup without explicit authorization. Never overwrite user work. No full V1 implementation in a foundation task.

Architecture baseline changes require evidence → proposed ADR → alternatives/trade-offs → human approval → synchronized docs/harness/tests → implementation. Accepted product rules remain in force while a proposal is pending. See [ADR governance](docs/adr/README.md). Routine choices within approved conventions do not need extra approval. If missing semantics block a feature, document the exact gap and continue independent authorized work.

## 필수 검증과 Definition of Done

- New business logic needs tests; bugs need regression tests where feasible. Never delete, disable, weaken assertions, or broaden exclusions just to make implementation pass.
- Run `python scripts/check_repository.py`, then `./gradlew check bootJar` and `./gradlew integrationTest` from `backend/` (PowerShell: `.\gradlew.bat`) as documented. Integration tests require working Docker and must fail rather than silently skip without it. Validate Compose and build the image when infra changes. CI is a required merge gate.
- Review architecture dependencies, migrations, security, changed contracts, secret exposure, Git diff and scope. Update docs for contract/architecture changes. No TODO may disguise required behavior. State clearly which checks passed, failed, or could not run; an unavailable check is not a pass.
- Follow [Definition of Done](docs/development/definition-of-done.md) and [Git workflow](docs/development/git-workflow.md). A feature is not complete while required checks fail. A PR review must explicitly consider authorization and frozen rules, even for a solo developer.
