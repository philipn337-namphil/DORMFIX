# ADR-014: Administrator dormitory scope

Status: Accepted - V1 authoritative decision

Date: 2026-09-18

## Context

Frozen ADMIN permissions are dormitory-scoped but the original ERD had no administrator-dormitory membership relationship.

## Decision

Use a separate `admin_dormitory_scopes` mapping table for V1 administrator dormitory scope.

- `SUPER_ADMIN` has effective scope over every Dormitory; no scope rows are required for this role.
- `ADMIN` may manage only Dormitories explicitly linked by `admin_dormitory_scopes`.
- An `ADMIN` with no scope rows has no manageable Dormitory and is never treated as globally scoped.
- `RESIDENT` and `WORKER` receive no scope rows.
- Whether the target User is an `ADMIN` is validated in the Application layer when a scope row is created.
- The table has composite primary key `(user_id, dormitory_id)`.
- It has an additional index on `(dormitory_id, user_id)`.
- Both User and Dormitory foreign keys use explicit `ON DELETE RESTRICT`.
- Scope revocation deletes the row. V1 has no `active`, `revoked_at`, or history columns.

The physical V1 migration is `V4__admin_dormitory_scopes.sql`. The structure management authorization policy is defined by [ADR-015](ADR-015-structure-management-authorization.md); exact Admin API paths and DTOs remain separate review items.

## Alternatives

Inferring staff authority from a resident Residence is unreliable. Treating `ADMIN` as global silently broadens permissions, so those alternatives are not selected.

## Trade-offs

The mapping table supports explicit multi-dormitory administration with minimal V1 state. It deliberately does not retain revocation history or encode role validation in the database.

## Consequences

Q02 is resolved for V1 physical storage. Preserve domain lifecycle rules and multi-role privacy when the later Application/API authorization slice is implemented.
