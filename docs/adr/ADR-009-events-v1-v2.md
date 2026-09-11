# ADR-009: Spring events now; Outbox/SQS later

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Durable RequestHistory is required, while V1 notification side effects may use internal events.

## Decision / 결정

Application publishes ID/value events within command transactions; side-effect listeners run AFTER_COMMIT. Notification writes use a separate REQUIRES_NEW service. Document V2 outbox/SQS boundary only.

## Alternatives / 대안

Synchronous external I/O inside the transaction is unsafe. Implementing an outbox now violates the scoped V1 direction.

## Trade-offs / 장단점

Internal events have a crash-loss window. Listener failure cannot roll back the command; delivery tolerance must be approved before notification release.

## Consequences / 결과

No exactly-once or durable-delivery claim. Audit history commits with core changes. V2 can add outbox publisher, retries and idempotent consumers.
