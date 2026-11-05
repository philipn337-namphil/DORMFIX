# ADR-022: MaintenanceRequest lifecycle command contract

Status: Accepted - V1 authoritative decision

Date: 2026-11-04

Human approval on 2026-11-04 accepted this contract as proposed, including that `close`, `reject`
and `mark-duplicate` end the active assignment in the same transaction while `resolve` does not.

## Context

The frozen V1 contract fixes more of this slice than of any previous one. The permission matrix
gives every transition its source, target and authorized relationship: `start` moves `ASSIGNED` to
`IN_PROGRESS`, `hold` moves `IN_PROGRESS` to `ON_HOLD`, `resume` moves `ON_HOLD` back to
`IN_PROGRESS` and `resolve` moves `IN_PROGRESS` to `RESOLVED`, each performed by the current active
worker. `close` and `reopen` act on `RESOLVED` and belong to the reporter `RESIDENT` or a scoped
`ADMIN`/`SUPER_ADMIN`. `reject` and `mark duplicate` act on `REPORTED` and belong to a scoped
`ADMIN`/`SUPER_ADMIN`. `CLOSED`, `REJECTED` and `DUPLICATE` are terminal, a `CLOSED` request cannot
be reopened, and a `SUPER_ADMIN` does not bypass any of it. The API inventory lists the eight paths
and states that all of them require the submitted entity version, with stale submissions answering
`409 VERSION_CONFLICT`. The ERD already carries `status_reason`, `resolved_at`, `closed_at` and
`duplicate_of_id`.

What the frozen contract does not specify:

1. the request DTO for each command, and which commands accept or require a reason;
2. the lifetime of `status_reason` across transitions - whether a later transition overwrites it,
   keeps it, or clears it;
3. when `resolved_at` and `closed_at` are written, and what `reopen` does to them;
4. the validation rules for `duplicate_of_id`. The ERD is explicit that this is open: it records
   only that duplicates reference the original and that there is "no invented chain/cycle policy";
5. the RequestHistory event types for the eight commands, which matters because the V7 check
   constraint admits only `REQUEST_CREATED`, `REQUEST_ASSIGNED` and `REQUEST_REASSIGNED`;
6. the precise meaning of the frozen `403 WORKER_NOT_ASSIGNED` code against `403 ACCESS_DENIED`;
7. whether a terminal transition ends the active assignment, and whether `reopen` may move a
   request to `IN_PROGRESS` when no active assignment exists.

## Decision

### One command shape, with two exceptions

Six commands take `LifecycleCommandRequest(statusReason?, version)`: `start`, `resume`, `resolve`,
`close`, `reopen`, and `hold`. Of these, `hold` requires `statusReason`; the other five accept it
as optional. `reject` takes the same shape but requires `statusReason`. `mark-duplicate` takes
`MarkDuplicateRequest(duplicateOfId, statusReason?, version)`.

`hold` and `reject` require a reason because each leaves the request in a state whose explanation
the next reader cannot reconstruct: work stopped, or the report was refused. `statusReason` is at
most 500 characters, matching the column.

No command accepts `status`, `assigneeId`, a lifecycle timestamp, `reporterId`, `priority` or
`duplicateOfId` except where stated above. The actor is always the JWT subject.

### status_reason reflects the current status, and RequestHistory is the audit

Every transition writes `status_reason`: to the supplied value when the command carries one, and to
`null` when it does not. It is a projection of why the request is in its present state, not a log.

The durable audit is the append-only RequestHistory row, which keeps the reason of every transition
even after `status_reason` has moved on. Accumulating reasons in the column would duplicate that
audit in a mutable field and would leave a resolved request displaying the reason it was once held.

### Lifecycle timestamps

`resolve` sets `resolved_at`. `close` sets `closed_at`. `reopen` clears `resolved_at` because the
request is no longer resolved, and never touches `closed_at` because a `CLOSED` request cannot be
reopened. Resolving again after a reopen overwrites `resolved_at`. `reject` and `mark-duplicate`
set neither: neither is a resolution nor a closure, and their own history row carries the time.

### duplicate_of_id

`mark-duplicate` requires `duplicateOfId`. The referenced original must exist and must be visible
to the actor under the ADR-020 read visibility rules; otherwise the response is
`404 REQUEST_NOT_FOUND`, identical to a missing request, so the command does not disclose requests
the actor may not read.

A request cannot be marked a duplicate of itself, and the original must not itself be `DUPLICATE`.
Both are `409 INVALID_REQUEST_STATE`. V1 therefore permits no duplicate chains at all: every
`duplicate_of_id` points at a request that is not a duplicate, which makes cycles impossible
without a graph walk. This is the narrowest rule that closes the gap the ERD left open.

`duplicate_of_id` is written once. `DUPLICATE` is terminal, so no later command can change it.

### Terminal transitions end the active assignment; resolve does not

`close`, `reject` and `mark-duplicate` end the active assignment when one exists, in the same
transaction, setting `unassigned_at` and `unassigned_by_id` to the acting user. Nobody is working a
closed, rejected or duplicate request, and leaving the row active would keep the request in the
worker listing for `assigned-to-me` forever.

`resolve` does not end the assignment. The matrix requires `reopen` to return the request to
`IN_PROGRESS`, which is only coherent while the worker who resolved it is still assigned.

`reopen` therefore requires an active assignment. Without one the response is
`409 INVALID_REQUEST_STATE`, and an administrator assigns a worker before reopening. Moving a
request to `IN_PROGRESS` with nobody assigned would contradict the matrix rule that only the
current active worker may act on it.

### Authorization and the two 403 codes

Authorization runs before any state check, so a conflict response never reveals the state of a
request the caller may not act on.

`start`, `hold`, `resume` and `resolve` require the caller to be the current active assignee. A
caller who holds the `WORKER` role but is not that assignee - an unassigned worker, or a former
assignee whose row has ended - receives `403 WORKER_NOT_ASSIGNED`. A caller whose role cannot use
the command at all receives `403 ACCESS_DENIED`. That split gives the frozen `WORKER_NOT_ASSIGNED`
code a precise meaning and keeps `ACCESS_DENIED` for role failures.

`close` and `reopen` accept the reporter when the caller is a `RESIDENT` whose id equals
`reporter_id`, or a scoped `ADMIN`/`SUPER_ADMIN`. `reject` and `mark-duplicate` accept only a
scoped `ADMIN`/`SUPER_ADMIN`. Scope resolution reuses `DormitoryScopeResolutionService`.

### Version, state and atomicity

The submitted `version` is compared before the command executes; a mismatch is
`409 VERSION_CONFLICT`, as is a flush-time optimistic-locking failure. A command whose source state
does not match the matrix is `409 INVALID_REQUEST_STATE`; commands are not idempotent, so repeating
a completed transition conflicts rather than succeeding silently.

One transaction per command performs the state change, the terminal assignment end where it
applies, the timestamp and reason writes, and exactly one append-only RequestHistory row.

### Audit rows and V8

The eight commands append `REQUEST_STARTED`, `REQUEST_HELD`, `REQUEST_RESUMED`, `REQUEST_RESOLVED`,
`REQUEST_CLOSED`, `REQUEST_REOPENED`, `REQUEST_REJECTED` and `REQUEST_MARKED_DUPLICATE`. Each
records the actor, the previous status, the new status, and the supplied reason.
`REQUEST_MARKED_DUPLICATE` also carries `{"duplicateOfId": <id>}` in `metadata`.

V8 replaces `ck_request_history_event_type` so it admits these eight alongside the existing three.
V6 and V7 are not edited; the constraint is dropped and recreated, as V7 already did.

The transitions are named commands on `MaintenanceRequest`, each validating its own source state.
There is no public status setter and no status PATCH.

## Contract synchronization

On approval, synchronize the eight command DTOs, the reason requirements, the `status_reason` and
timestamp policies, the `duplicate_of_id` rules, the terminal-assignment rule, the `reopen`
precondition, the `WORKER_NOT_ASSIGNED` versus `ACCESS_DENIED` split, and the eight event types
into the frozen API specification and permission matrix, record the V8 constraint change in the
ERD notes, and refresh the frozen baseline hash. Implementation follows in the same requested
slice.

## Alternatives

### 1. Accumulate reasons in `status_reason`

Keeping every reason in the column would make the latest reason visible without reading history. It
was rejected because the column is bounded at 500 characters, the append-only RequestHistory
already holds the full sequence, and a resolved request would still display why it was once held.

### 2. Leave the active assignment in place on close, reject and mark-duplicate

This keeps lifecycle commands away from assignment rows, so each concern has one writer. It was
rejected because a terminal request would keep an active assignment forever and would stay in every
`assigned-to-me` listing, which the read contract offers no way to filter out.

### 3. Let `reopen` move a request to `IN_PROGRESS` with no active assignment

This avoids a second step for an administrator whose worker has been unassigned. It was rejected
because `IN_PROGRESS` with nobody assigned contradicts the matrix rule that only the current active
worker may act, and would leave `start`, `hold` and `resume` unusable on that request.

### 4. Allow duplicate chains, resolving to the ultimate original

A chain tolerates an administrator marking B duplicate of A and later A duplicate of Z. It needs a
cycle check and a resolution walk on every read. Rejecting a `DUPLICATE` original keeps the
invariant to one comparison and still lets an administrator point B at Z directly.

### 5. Make `duplicateOfId` of an invisible request `403`

This distinguishes "not yours" from "not there". It was rejected for the same reason ADR-020 chose
`404` on reads: request ids are sequential, so a `403` here would let an administrator probe other
dormitories through the command.

### 6. Require `statusReason` on every command

Uniform payloads are simpler to document. It was rejected because `start`, `resume` and `close` on a
resolved request need no explanation, and forcing one produces filler text that degrades the audit.

## Consequences

This slice completes the REPORT to CONFIRM workflow for the request aggregate and makes
`WORKER_NOT_ASSIGNED` and the version-checked command protocol load-bearing across eight commands.
It introduces the first lifecycle writes to `status_reason`, `resolved_at` and `closed_at`, and the
first case of a lifecycle command ending an assignment row.

Because terminal transitions end the active assignment, a worker listing never shows terminal work,
but the assignment history of a closed request now always ends with an administrator as
`unassigned_by_id` rather than the reassigning administrator.

Implementation observation, recorded after the slice was built: of the three terminal transitions,
only `close` can actually have an assignment to end. `reject` and `mark-duplicate` leave `REPORTED`,
and a `REPORTED` request has never been assigned because assignment moves it to `ASSIGNED` in the
same transaction. The rule is therefore vacuous for those two today. It is kept and enforced in
both of them anyway, so the invariant holds without depending on that coincidence, and a later
slice that widens either source state inherits the correct behavior rather than a gap.

Rejecting a `DUPLICATE` original means a client that wants to point at the ultimate original must
read the chain itself; V1 guarantees there is none to read.

Visits, work logs, attachments and comments stay out of scope, as does request metadata PATCH.
