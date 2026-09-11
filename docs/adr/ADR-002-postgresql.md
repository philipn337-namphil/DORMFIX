# ADR-002: PostgreSQL

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

The frozen ERD requires TIMESTAMPTZ, JSONB, relational integrity and practical partial unique indexes.

## Decision / 결정

Use PostgreSQL 17 for local/Testcontainers and a compatible supported RDS PostgreSQL version for deployment. Start from pinned postgres:17.6-alpine, updating patches through review.

## Alternatives / 대안

H2 is not faithful for constraints/time/JSONB. MySQL would change DB capabilities. A document database would weaken the approved model.

## Trade-offs / 장단점

Real integration tests need Docker; this cost is justified for assignment concurrency and migration fidelity.

## Consequences / 결과

Schema constraints and indexes are tested against PostgreSQL. RDS major/patch compatibility is checked before provisioning; no cloud instance is created now.
