# ADR-014: Administrator dormitory scope gap

Status: Proposed - requires human approval; not implemented

Date: 2026-09-10

## Context

Frozen ADMIN permissions are dormitory-scoped but ERD has no administrator-dormitory membership relationship.

## Decision

PROPOSED ONLY: evaluate explicit staff scope mapping versus configured single-dormitory operational scope; obtain human approval before persistence or authorization implementation.

## Alternatives

Inferring staff authority from a resident Residence is unreliable. Treating ADMIN as global silently broadens permissions.

## Trade-offs

A new scope table changes the frozen ERD; configuration may limit future multi-dormitory operations. Neither is silently chosen.

## Consequences

Q02 blocks scoped admin slices. Preserve domain lifecycle rules and multi-role privacy while designing the approved scope contract.
