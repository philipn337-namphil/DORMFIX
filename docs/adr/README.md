# Architecture decision records

ADRs 001-012 record the engineering choices requested for this foundation and await overall baseline acceptance. They do not grant permission to alter frozen product semantics. ADRs 013-014 are unresolved proposals and must not be implemented until explicitly approved.

For future changes: evidence/problem -> proposed ADR -> alternatives/trade-offs -> human approval -> synchronized architecture/product docs and AGENTS -> implementation/tests. Never silently change package architecture, DB conventions, state machine, authorization, transaction/event model, API, deployment or testing policy. Routine work within established conventions does not need a new ADR.

Statuses: Proposed, Accepted, Rejected, Superseded. Record approval date/reference when accepted. Preserve superseded decisions with a link to replacements rather than editing history. New records use the next number and include context, decision, alternatives, trade-offs, consequences, affected contracts, validation and approval evidence. A proposal cannot override a frozen baseline while pending.

## Index

- [ADR-001: Java 21 and Spring Boot](ADR-001-java21-spring-boot.md)
- [ADR-002: PostgreSQL](ADR-002-postgresql.md)
- [ADR-003: Pragmatic modular monolith](ADR-003-modular-monolith.md)
- [ADR-004: Package by feature](ADR-004-package-by-feature.md)
- [ADR-005: Explicit lifecycle commands](ADR-005-command-apis.md)
- [ADR-006: Request optimistic locking](ADR-006-optimistic-locking.md)
- [ADR-007: Flyway and physical mapping conventions](ADR-007-flyway-jpa-conventions.md)
- [ADR-008: Private S3 presigned attachments](ADR-008-s3-presigned-uploads.md)
- [ADR-009: Spring events now; Outbox/SQS later](ADR-009-events-v1-v2.md)
- [ADR-010: Ubuntu EC2 first deployment](ADR-010-ubuntu-ec2.md)
- [ADR-011: Docker and local Compose](ADR-011-docker-local-runtime.md)
- [ADR-012: Vertical feature branches and solo review](ADR-012-vertical-git-workflow.md)
- [ADR-013: Refresh-token persistence gap](ADR-013-refresh-token-storage.md)
- [ADR-014: Administrator dormitory scope gap](ADR-014-administrator-scope.md)
