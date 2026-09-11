# Definition of Done

A feature is ready for human acceptance only when:

- Scope and frozen rules are understood; relevant AGENTS/docs and existing code/tests were read.
- Work is on a vertical feature/fix branch, with no unrelated changes.
- API commands, DTO validation, stable error/status semantics and concurrency versions match contract.
- Authentication, role, ownership/current assignment/scope and state checks are implemented and tested, including negative cases.
- Domain transitions/invariants, consent snapshots, retention and terminal states are preserved.
- Required Flyway migration is reviewed; PostgreSQL constraints/mappings and upgrade path are verified.
- State/history commit atomically; external effects run after commit, with honest failure semantics.
- New business logic and feasible bug regressions have tests; relevant API/integration/architecture tests pass.
- `python scripts/check_repository.py`, Gradle check/bootJar/integrationTest pass; image/Compose gates pass where relevant.
- No tests were weakened/deleted to obtain green results; no TODO conceals required behavior.
- No entity leaks, N+1/unbounded query surprises, dependency cycles, secret/PII leakage or permissive security defaults remain.
- Contract/architecture/operations docs are updated; baseline changes have approved ADRs.
- Git diff, migrations, CI results and deployment impact were reviewed; limitations and deferred work are explicitly reported.
- The human accepts the concrete PR-style result; push/merge/deployment happens only when authorized.

An unavailable required check is recorded as blocked, not passed. Phase 0 acceptance separately acknowledges open product contracts and deferred feature code; it does not waive future feature gates.
