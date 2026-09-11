# Testing policy and required gates

New business logic requires tests; bug fixes require regression tests where feasible. Never weaken/remove/disable assertions or broaden exclusions to make an implementation pass. Features are incomplete while required checks fail. No arbitrary coverage percentage replaces behavior evidence.

| Layer | Purpose | Required examples as slices arrive |
|---|---|---|
| Domain unit | Plain Java invariants/transitions | Every legal/illegal transition; terminal states; reassign -> ASSIGNED; immutable visit consent |
| Application unit | Authorization and orchestration | owner/non-owner, current/former worker, admin scope, multi-role, stale version, required history |
| PostgreSQL integration | Persistence/transactions/concurrency | Flyway schema, mapping, partial unique index, residence constraints after review, rollback history atomicity, competing versions |
| API | HTTP contract and privacy | 400/401/403/404/409, strict DTO fields, no STAFF_ONLY leakage, version required, safe errors |
| Architecture | Dependency rules | API cannot reach persistence/domain, domain isolation, application adapters, no controller transaction, feature cycles |
| Runtime smoke | Deployment wiring | safe liveness/readiness, DB readiness, image non-root, denied sensitive endpoints |

From backend: `./gradlew check bootJar` (Windows `gradlew.bat`) runs unit/API/ArchUnit and Checkstyle. `./gradlew integrationTest` is a separate REQUIRED merge gate using Docker/Testcontainers. Separation makes quick local feedback possible without pretending a no-Docker run is complete. CI always runs both; no disabledWithoutDocker or ignored failing tests. `compileIntegrationTestJava` proves compilation only, not test execution.

Phase 0 has no business entities, so domain/application rules allow empty selections explicitly until slices arrive. They still evaluate every matching future class. Existing API/platform/cycle rules execute against real scaffold code. Remove the empty-selection allowances when business classes exist. Review supplements static tests for entity setters, JPA fetch behavior and cross-feature authorization.

Foundation integration tests start PostgreSQL, boot the real application, validate/reapply Flyway harmlessly and check safe operational endpoints. Assignment/residence/index tests are deferred with the corresponding production schema, not fake sample tables. Do not claim foundation tests prove absent business behavior.

Transaction tests should use actual committed boundaries: avoid test-wide rollback masking AFTER_COMMIT listeners. Use distinct connections/transactions for concurrency; synchronize contenders deterministically. Verify failed command writes no history and no after-commit effects. Notification write tests prove a new transaction commits. Readiness failure tests should verify 503 under DB loss when operational coverage expands.

Before infra merge: `docker compose config --quiet`, `docker build -t dormfix:local backend`, full Compose readiness smoke and production-template syntax review. Before release: upgrade migration test, security review, backup/restore rehearsal and image/runtime checks. Record exact unavailable checks and causes; unavailability is not success.

Repository-guard regression tests: `python -m unittest discover -s scripts -p 'test_*.py'` from root. These use isolated temporary copies to prove frozen-file, link, wrapper, configuration and CI-gate violations fail. CI runs them with the offline guard.
