# Foundation recovery and validation record

## Checkpoint 1 - recovered 2026-09-11

Before edits, inspected root/scoped AGENTS, README, all product/architecture/development/operations documents, 14 ADRs, backend source/tests/config, Docker/Compose/Nginx/systemd and GitHub CI. Saved file hashes in ignored .tools/resume-baseline.json for comparison during this run.

Git: local unborn chore/foundation branch, no commits/remotes or tracked diff; existing work is untracked. The prior sandbox account owns .git. Read-only inspection uses git -c safe.directory=C:/Users/phili/Desktop/DORMFIX, not global Git configuration. No ownership/config changes, commit, push or deployment were performed.

Already saved: all 25 foundation deliverable categories except the repository guard implementation/final validation. Existing reports contain 6 passing ArchUnit tests, 3 passing API/security tests and clean Checkstyle, with dormfix.jar built. PostgreSQL integration was compiled but not executed. Docker Engine is now available.

First incomplete checkpoint: missing scripts/check_repository.py and frozen hash manifest despite documentation/CI references; final validation report was a placeholder. Two question marks replaced intended arrows in product prose; root validation command incorrectly repeated backend/ after changing directory. Corrections are typographical/command repairs only, with no frozen semantic changes.

## Checkpoint 2 - harness completion

Added offline repository guard and canonical-LF contract manifest. It checks required files, local documentation links, Gradle publisher checksums, frozen contracts and selected configuration/CI/test-bypass controls. Product documents retain the same columns, APIs, roles and transitions. Remaining checkpoints and executed validation results are recorded below as work completes.

## Checkpoints B-E - completed 2026-09-11

- `backend/gradlew.bat check bootJar`: PASS (Java 21 toolchain, Spring Boot compile, Checkstyle, ArchUnit and API/security tests).
- `backend/gradlew.bat build integrationTest`: PASS. PostgreSQL Testcontainers ran all 3 integration tests: Flyway validate/repeatable startup, safe liveness/readiness, and denied business/sensitive actuator access. Result: 3 tests, 0 failures/errors/skips.
- Repository guard and its 7 regression tests: PASS.
- `docker compose config --quiet`: PASS with ephemeral `POSTGRES_PASSWORD` supplied through the process environment; no credential was written to disk.
- `docker build --tag dormfix:foundation-local backend`: PASS. Multi-stage non-root image built; embedded Gradle check/bootJar passed. The image is local-only and is not pushed.
- Docker daemon was started for validation. No AWS resource, server, DNS, TLS certificate, GitHub remote, commit or deployment was created.

The production Compose template intentionally references `/etc/dormfix/app.env`, which does not exist in the repository; local `docker compose -f infra/production/compose.yml config` therefore fails until an operator creates that mode-0600 secret file on the target host. This is a deliberate safety boundary, documented in the Ubuntu runbook, rather than a missing committed file. The template was inspected for syntax and image/secret/restart controls; it was not started.

Final review found no frozen product transition/API/ERD change. Corrected two prose arrows and one backend command path only. Open review items remain in `docs/architecture/open-questions.md`; no business feature code or migrations beyond Flyway V1 foundation were added.
