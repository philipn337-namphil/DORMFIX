# ADR-006: Request optimistic locking

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Concurrent residents, workers and administrators must not overwrite each other or create multiple active assignments.

## Decision / 결정

Use JPA @Version on MaintenanceRequest, required submitted command versions and 409 VERSION_CONFLICT. Reinforce active assignment with a PostgreSQL partial unique index.

## Alternatives / 대안

Blind last-write-wins loses updates. Universal pessimistic locking adds contention without evidence.

## Trade-offs / 장단점

Clients must reload/reconcile after conflict; pre-check alone is insufficient without flush-time optimistic locking.

## Consequences / 결과

Command, assignment and history mutations are atomic. Integration tests cover competing transactions and constraint violations with the assignment slice.
