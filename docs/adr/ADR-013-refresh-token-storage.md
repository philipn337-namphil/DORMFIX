# ADR-013: Refresh-token persistence gap

Status: Proposed - requires human approval; not implemented

Date: 2026-09-10

## Context

Frozen endpoints require refresh/logout, but the 15-entity ERD has no refresh-token persistence or revocation model.

## Decision

PROPOSED ONLY: approve a narrowly scoped technical refresh-token store with hashed opaque tokens, expiry, rotation, family/reuse detection and revocation, plus a reviewed migration. Do not add it yet.

## Alternatives

Pure stateless refresh tokens cannot provide strong immediate rotation/reuse/logout revocation alone. Redis introduces infrastructure without current authorization. A managed identity provider changes auth ownership and needs separate review.

## Trade-offs

Additional storage may extend the frozen baseline and therefore requires explicit human approval, not an engineering assumption. Browser cookie/CSRF choices and access-token residual lifetime also need review.

## Consequences

Phase 1 must resolve Q01/Q14 before implementing token issuance/refresh/logout. Keep scaffold deny-all until an approved contract is implemented.
