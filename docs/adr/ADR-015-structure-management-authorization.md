# ADR-015: Dormitory structure management authorization

Status: Accepted - V1 authoritative decision

Date: 2026-09-18

## Context

The frozen permission matrix and API inventory identified structure management roles but left the operation-level policy and MaintenanceCategory ownership ambiguous. The prior documents also did not define parent deactivation propagation or deletion behavior.

## Decision

- `SUPER_ADMIN` alone may create, update, or deactivate Dormitory, Building, and Space.
- `ADMIN` may create, update, or deactivate Facility only within an explicitly assigned Dormitory scope. `SUPER_ADMIN` may manage Facility globally.
- MaintenanceCategory is V1 global Master Data. Its creation, update, and deactivation are `SUPER_ADMIN`-only.
- Deactivating a parent does not automatically deactivate children.
- Creating or reactivating a child below an inactive parent is denied.
- Operational data is never hard-deleted.
- The accepted Admin API contract, including paths, DTOs and Facility status commands, is recorded in [ADR-016](ADR-016-admin-structure-api-contract.md).

## Alternatives

Allowing ADMIN to manage global MaintenanceCategory would conflict with its global master-data role and would not have a Dormitory FK for scope enforcement. Cascading deactivation would create implicit state changes across aggregates. Neither alternative is selected.

## Consequences

The permission matrix, API inventory, security guidance, and open-question register now share one V1 policy. Application authorization must enforce these rules before management APIs are implemented; no Java or database schema change is implied by this ADR.
