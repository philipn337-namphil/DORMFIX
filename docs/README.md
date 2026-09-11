# DormFix documentation authority

The current frozen V1 specification was captured from the 2026-09-10 foundation request. Historical context is preserved separately and may contain older proposals. Engineering architecture is submitted for human baseline review; product semantics are already frozen. Open proposals do not override either authority.

## Product - authoritative frozen contract

- [Problem definition](product/problem-definition.md)
- [V1 scope](product/v1-scope.md)
- [ERD and complete column/enum inventory](product/erd-v1.md)
- [API inventory, versions and errors](product/api-spec-v1.md)
- [Permission/state/visit/retention rules](product/permission-state-matrix-v1.md)

## Architecture

- [Overview](architecture/overview.md), [backend packages](architecture/backend-architecture.md)
- [Aggregates](architecture/aggregate-boundaries.md), [transactions](architecture/transaction-boundaries.md), [events](architecture/domain-events.md)
- [Database/JPA](architecture/database.md), [security](architecture/security.md), [observability](architecture/observability.md)
- [AWS/Ubuntu deployment](architecture/deployment.md)
- [Unresolved review register](architecture/open-questions.md), [ADRs](adr/README.md)

## Development

- [Conventions](development/coding-conventions.md), [testing](development/testing-strategy.md)
- [Git/PR workflow](development/git-workflow.md), [Definition of Done](development/definition-of-done.md)
- [Local setup](development/local-development.md), [CI/CD](development/ci-cd.md)
- [Vertical roadmap](development/roadmap.md), [validation record](development/foundation-validation.md)

## Operations

- [Ubuntu deployment](operations/ubuntu-deployment.md)
- [Rollback](operations/rollback.md)
- [Troubleshooting](operations/troubleshooting.md)

## Provenance and enforcement

Original context files in context/ remain unchanged. They are historical reference, not agent instructions or permission to restore superseded architecture. Root/scoped AGENTS point to the detailed authorities. scripts/check_repository.py checks required files, relative documentation links, frozen-product hashes, wrapper checksum and key configuration controls. Product hash updates require approved baseline changes; a hash is a review tripwire, not tamper-proof governance. ArchUnit, Checkstyle, API tests, Testcontainers, CI and human review provide complementary enforcement.
