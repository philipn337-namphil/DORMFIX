# ADR-005: Explicit lifecycle commands

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context

Frozen V1 defines authorized transitions rather than arbitrary resource status edits.

## Decision

Retain every explicit command endpoint and the constrained metadata PATCH. Aggregate methods control transitions.

## Alternatives

Generic CRUD status mutation violates the frozen product model. A workflow engine adds no current value.

## Trade-offs

More named endpoints/services make business intent and negative tests visible.

## Consequences

No SUPER_ADMIN override, no terminal reopening, no visit-completion-as-resolution.
