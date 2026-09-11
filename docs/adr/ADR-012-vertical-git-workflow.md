# ADR-012: Vertical feature branches and solo review

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context

Codex-assisted solo development needs scope control and review evidence even with one human owner.

## Decision

Use feature/use-case branches and PR-style review through API/application/domain/persistence/tests/docs. Foundation uses chore/foundation. No feature development on main.

## Alternatives

Layer branches fragment business behavior and complicate integration. Direct main development loses review gates.

## Trade-offs

Self-authored PRs may not receive self-approval in GitHub; use recorded human review checklist and required CI rather than an impossible review setting.

## Consequences

Human owns final acceptance. No automatic pushes/deployments. Preserve migration ordering and request architecture approval for genuine baseline changes.
