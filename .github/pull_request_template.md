## Problem and resulting behavior

Describe the concrete use case, before/after behavior and bounded vertical slice.

## Contracts and risks

API/schema/authorization/state changes, migration compatibility, ADR links and operational impact. Explicitly state any proposed frozen-baseline change; do not silently change it.

## Validation

List exact checks and results, including PostgreSQL/API/architecture tests and any unavailable check. Link reports where available.

## Review checklist

- [ ] Relevant AGENTS, frozen specification and existing code read
- [ ] Correct vertical branch; no unrelated edits
- [ ] Role, ownership/current assignment/scope and state checked
- [ ] Terminal states, consent and version/history invariants preserved
- [ ] Migration and rollback compatibility reviewed when applicable
- [ ] Required tests/build pass; no tests weakened
- [ ] No secrets/PII leakage; docs updated
- [ ] Full diff reviewed in a separate pass
- [ ] Human acceptance recorded before merge
