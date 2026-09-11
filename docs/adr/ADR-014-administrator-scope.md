# ADR-014: Administrator dormitory scope gap

Status: Proposed - requires human approval; not implemented

Date: 2026-09-10

## Context / 배경

Frozen ADMIN permissions are dormitory-scoped but ERD has no administrator-dormitory membership relationship.

## Decision / 결정

PROPOSED ONLY: evaluate explicit staff scope mapping versus configured single-dormitory operational scope; obtain human approval before persistence or authorization implementation.

## Alternatives / 대안

Inferring staff authority from a resident Residence is unreliable. Treating ADMIN as global silently broadens permissions.

## Trade-offs / 장단점

A new scope table changes the frozen ERD; configuration may limit future multi-dormitory operations. Neither is silently chosen.

## Consequences / 결과

Q02 blocks scoped admin slices. Preserve domain lifecycle rules and multi-role privacy while designing the approved scope contract.
