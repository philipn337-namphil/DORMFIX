# ADR-006: Request optimistic locking

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context

Concurrent residents, workers and administrators must not overwrite each other or create multiple active assignments.

## Decision

Use JPA @Version on MaintenanceRequest, required submitted command versions and 409 VERSION_CONFLICT. Reinforce active assignment with a PostgreSQL partial unique index.

## Alternatives

Blind last-write-wins loses updates. Universal pessimistic locking adds contention without evidence.

## Trade-offs

Clients must reload/reconcile after conflict; pre-check alone is insufficient without flush-time optimistic locking.

## Consequences

Command, assignment and history mutations are atomic. Integration tests cover competing transactions and constraint violations with the assignment slice.
