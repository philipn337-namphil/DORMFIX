# ADR-016: Admin structure API contract

Status: Accepted - V1 authoritative decision

Date: 2026-09-20

## Context

The structure-management authorization policy was accepted in ADR-015, but the Admin API inventory still left the exact management paths, DTO mutability and Facility lifecycle commands open. The API contract must be frozen before Java Controllers and Application Services are implemented.

## Decision

- Use the `/admin/...` paths and allow-listed request DTOs recorded in the [V1 API contract](../product/api-spec-v1.md) for Dormitory, Building, Space, Facility and MaintenanceCategory management.
- Dormitory/Building/Space management uses the proposed create, metadata update and deactivate endpoints. Dormitory `name` has no V1 unique constraint and duplicate names do not map to a 409 conflict.
- Facility status changes are explicit commands: `/out-of-service`, `/reactivate` and `/retire`. The allowed transitions are `ACTIVE -> OUT_OF_SERVICE -> ACTIVE`, `ACTIVE -> RETIRED`, and `OUT_OF_SERVICE -> RETIRED`. `RETIRED` is terminal. Facility PATCH cannot change status.
- MaintenanceCategory `parentId` is accepted only by the create DTO. It is not accepted by the update DTO, and V1 has no category move endpoint.
- The remaining paths, DTO fields, roles, scope rules and standard error contract remain as recorded in the V1 API and permission documents. No Java Controller or Application Service is implied by this documentation decision.

## Alternatives

Allowing arbitrary status PATCH would make terminal-state enforcement implicit and weaken the command boundary. Providing a category move endpoint would create additional cycle, parent-state and authorization semantics before they are needed. Enforcing Dormitory name uniqueness would add a product rule not required by V1.

## Consequences

Clients can rely on stable, explicit Facility commands and immutable category parent relationships after creation. The implementation slice must enforce the documented transitions, scope authorization and error meanings without adding generic status/delete endpoints.
