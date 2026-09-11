# ADR-007: Flyway and physical mapping conventions

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context

Frozen logical columns need reproducible migrations without silent schema mutation.

## Decision

Flyway is authoritative; Hibernate validates only. snake_case names, app_user table, BIGINT identity IDs, string enums with CHECK constraints and Instant/TIMESTAMPTZ are mapping conventions. Phase 0 has a no-business-DDL foundation migration.

## Alternatives

Hibernate update lacks reviewed migration history. Native PostgreSQL enums make evolution more coupled; ordinal enums are unsafe. Liquibase adds no requirement-backed advantage here.

## Trade-offs

Unspecified logical nullability/defaults/user_roles details still require review in each owning slice. No convention authorizes extra business fields.

## Consequences

Applied migrations are immutable. Use expand/contract and separate production migration identity. Test migration startup and future constraints on PostgreSQL.
