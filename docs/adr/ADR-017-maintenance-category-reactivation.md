# ADR-017: MaintenanceCategory reactivation command

Status: Accepted - V1 authoritative decision

Date: 2026-09-30

## Context

The accepted structure-management policy says that creating or reactivating a child below an
inactive parent is denied. The Frozen API table defines an explicit category deactivate command,
but does not define its reactivation counterpart. Consequently, the intended category lifecycle
is incomplete and the current implementation must not be treated as contract authority.

## Proposed decision

Add the following explicit command to the V1 structure-management contract after approval:

| Method | Path | Request | Authorization | Success |
|---|---|---|---|---|
| POST | `/api/v1/admin/categories/{categoryId}/reactivate` | empty `ReactivateCommand` | `SUPER_ADMIN` only | `200 OK` with the normal `MaintenanceCategoryResponse`, where `active` is `true` |

The command is valid only for an inactive category. It checks the immediate parent when one is
present. A missing category or parent is `404 CATEGORY_NOT_FOUND`; an active category is `409
CATEGORY_ALREADY_ACTIVE`; and an inactive parent is `409 INACTIVE_PARENT`. The latter has the
same meaning as the existing structure rule: no child creation or reactivation below an inactive
parent. Repeated commands are not silently idempotent because they conceal a state conflict.

This proposal preserves these existing contract boundaries:

- `parentId` is accepted only at creation and remains immutable afterwards.
- V1 has no category move endpoint.
- Category metadata PATCH remains allow-listed and must not accept `active`, `status`, or arbitrary
  fields.
- Parent deactivation does not cascade to child categories.
- V1 provides no hard-delete endpoint.

## Required contract synchronization after approval

The Frozen API table must add the exact row above. Its text should state that the response is the
normal category response and that `INACTIVE_PARENT` is a 409 business-state conflict. The Frozen
permission/state matrix must add: “MaintenanceCategory reactivation is SUPER_ADMIN-only; it is
denied with 409 INACTIVE_PARENT when its parent is inactive. Parent deactivation does not
propagate.” ADR-015 and ADR-016 should then be synchronized as accepted decisions.

Frozen product files and `frozen-baseline.sha256.json` are synchronized with this approved decision.

## Alternatives

### 1. Do not provide reactivate; make deactivate terminal

This reduces the API surface and makes inactive categories easier to reason about. It conflicts
with the established child-reactivation policy, forces replacement categories for corrections,
and leaves an asymmetry with the other structure aggregates. It also makes accidental
deactivation irreversible in V1.

### 2. Permit a generic `active` or `status` PATCH

This makes clients issue fewer endpoint shapes. It weakens the command boundary, permits mass
assignment mistakes, makes state-specific authorization and conflict handling implicit, and is
inconsistent with Facility's explicit lifecycle commands. It is not recommended.

## Consequences

The recommended command gives a narrow, auditable lifecycle transition without introducing a
category move, cascade, or delete semantic. Approval is required before the implementation may be
considered contract-conforming or before Frozen hashes are refreshed.
