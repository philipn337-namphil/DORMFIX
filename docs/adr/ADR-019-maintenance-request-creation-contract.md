# ADR-019: MaintenanceRequest creation contract

Status: Accepted - V1 authoritative decision

Date: 2026-10-02

## Context

The frozen V1 contract defines `POST /api/v1/maintenance-requests`, the MaintenanceRequest
columns, `REPORTED` as the initial lifecycle state, and that a RESIDENT creates their own
request. It does not specify the create DTO, request-number algorithm, the reporter-to-space
relationship, or reference-object state rules. Those omissions affect authorization, persistence,
and API errors, so they must not be inferred from implementation convenience.

## Decision

Use a resident-only, current-residence reporting contract for V1.

| Item | Proposal |
|---|---|
| Actor | An authenticated `RESIDENT` only; `reporterId` is always the JWT subject. |
| Create DTO | `spaceId`, optional `facilityId`, `categoryId`, `title`, `description`, `entryPolicy`, `contactBeforeEntry`, optional `preferredVisitStart`, optional `preferredVisitEnd`. It contains no reporter, status, priority, duplicate, lifecycle timestamp, or version field. |
| Location authorization | `spaceId` must be the RESIDENT's current Residence room. A resident cannot report a common-area or another resident's room in V1. |
| Reference validity | The room and its Building/Dormitory hierarchy must be active. The category must be active. When `facilityId` is supplied, it must belong to `spaceId` and must not be `RETIRED`; `OUT_OF_SERVICE` remains reportable because it can itself be the subject of a maintenance report. |
| Server-owned fields | The server sets `status=REPORTED`, `priority=category.defaultPriority`, `duplicateOfId=null`, timestamps, and initial `version`. |
| Preferred visit time | Both timestamps are absent or both are present; when present `preferredVisitStart < preferredVisitEnd`. They express a preference rather than a scheduled Visit. |
| Request number | After the database ID is allocated, set a stable server-generated `DF-<id>` request number. The ID is not exposed as a substitute for this public number. |
| Audit | The create transaction persists the request and one append-only RequestHistory row with event type `REQUEST_CREATED`, actorId=reporterId, and newStatus=`REPORTED`. |

Creation returns `201 Created`, a Location header for `/api/v1/maintenance-requests/{id}`, and
the normal request response including `id`, `requestNumber`, `status`, `priority`, and `version`.

Missing referenced resources are `404` using the existing representative codes. A facility that
does not belong to the submitted space, an inactive hierarchy/category, a retired facility, or a
space outside the reporter's current residence is a `409 INVALID_REQUEST_STATE`. Malformed input,
an invalid EntryPolicy, or an invalid preferred time range is `400`.

## Contract synchronization

The exact create DTO, success response, reference-state rules, request-number algorithm, and
error meanings are synchronized with the Frozen API specification and permission matrix. The ERD
records the creation invariants and `REQUEST_CREATED` audit row. The Frozen baseline hash is
refreshed with those contract changes. Java and Flyway implementation remain deferred.

## Alternatives

### 1. Allow a resident to report any active Space

This supports common-area reporting without a separate workflow. It weakens location ownership
and makes authorization depend on rules not present in V1. A later explicit common-area report
path can add that capability without broadening the resident boundary now.

### 2. Reject all non-ACTIVE facilities

This keeps references uniform, but prevents a resident from reporting a facility already marked
out of service. Retired facilities are no longer operational; OUT_OF_SERVICE instead represents a
condition that can reasonably be reported and repaired.

### 3. Generate a random public request number before persistence

This avoids a two-step save but needs collision handling and a format policy. The database ID is
already unique and makes `DF-<id>` deterministic, auditable, and simple for V1.

## Consequences

This decision supplies a narrow, testable initial reporting boundary while preserving separate
assignment, lifecycle, visit, attachment, and communication work for later slices. It authorizes
contract-conforming implementation in a later explicitly requested slice; it does not itself add
Java or Flyway implementation.
