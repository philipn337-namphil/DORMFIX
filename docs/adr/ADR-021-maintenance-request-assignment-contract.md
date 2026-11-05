# ADR-021: MaintenanceRequest assignment contract

Status: Accepted - V1 authoritative decision

Date: 2026-11-04

Human approval on 2026-11-04 accepted this contract as proposed, including the `409 INVALID_WORKER`
mapping, `409 REQUEST_ALREADY_ASSIGNED` for both the duplicate-assignee command and the
active-assignment race, and the assignment table as the single source of truth with no
denormalized `assignee_id` column.

## Context

The frozen V1 contract already fixes more of this slice than it did for creation or reads. The
API inventory lists `POST /maintenance-requests/{id}/assignments` for initial assignment and
reassignment, and records that it requires the submitted entity version. The permission matrix
fixes the transitions: `assign` moves `REPORTED` to `ASSIGNED`, `reassign` moves `ASSIGNED`,
`IN_PROGRESS` or `ON_HOLD` back to `ASSIGNED`, both are performed by an `ADMIN` within dormitory
scope or a `SUPER_ADMIN`, and reassignment ends the existing assignment and creates a new one that
the new worker must start again. The ERD fixes the table shape - `id`, `request_id`, `worker_id`,
`assigned_by_id`, `assigned_at`, nullable `unassigned_at`, nullable `unassigned_by_id`, nullable
`reason` - and names the physical guard: `CREATE UNIQUE INDEX ux_assignment_active_request ON
maintenance_assignment(request_id) WHERE unassigned_at IS NULL`.

What the frozen contract deliberately leaves open is the error mapping. It names
`REQUEST_ALREADY_ASSIGNED`, `INVALID_WORKER`, `WORKER_NOT_FOUND` and `WORKER_NOT_ASSIGNED` as
representative codes and then says plainly that the exact validation-versus-business-conflict
mapping must be specified by assignment API review, and that not every integrity constraint error
may be mapped to `VERSION_CONFLICT`. It also does not specify:

1. the request DTO for the assignment command;
2. what makes a named user a valid assignment target, and whether a worker is restricted to a
   dormitory;
3. what happens when the named worker is already the active assignee;
4. which error the `ux_assignment_active_request` race produces;
5. the RequestHistory event types for assignment and reassignment, which matters because the V6
   check constraint currently admits only `REQUEST_CREATED`;
6. how the read slice derives an `assigneeId`.

ADR-020 is accepted but cannot be implemented on its own: its `WORKER` visibility rule,
`assigned-to-me` path, `assigneeId` filter and `assigneeId` read field all require assignment
rows, and V6 created no assignment table and no `assignee_id` column. This slice therefore lands
first and unblocks the full read contract.

## Decision

### Command and actor

`POST /maintenance-requests/{id}/assignments` takes exactly
`AssignWorkerRequest(workerId, reason?, version)`. It never accepts `status`, `assignedById`,
`assignedAt`, `unassignedAt`, or an assignment identifier. `assignedById` is always the JWT
subject.

The actor is an `ADMIN` whose scope contains the dormitory that the request room resolves to, or a
`SUPER_ADMIN`. Scope resolution reuses `DormitoryScopeResolutionService`. A wrong role, a
scope-less `ADMIN`, or an `ADMIN` outside the request dormitory is `403 ACCESS_DENIED`.

Because the actor must already be able to see the request to act on it, this path keeps `403` for
authorization failures. The `404` rule in ADR-020 applies only to read paths.

### One path, two behaviors, selected by current state

| Current status | Behavior | Resulting status |
|---|---|---|
| `REPORTED` | initial assignment | `ASSIGNED` |
| `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD` | reassignment: end the active assignment, create a new one | `ASSIGNED` |
| `RESOLVED`, `CLOSED`, `REJECTED`, `DUPLICATE` | rejected | unchanged |

A rejected state is `409 INVALID_REQUEST_STATE`. `RESOLVED` is rejected because the matrix does not
list it as a reassignment source; a resolved request is reopened first. `CLOSED`, `REJECTED` and
`DUPLICATE` are terminal and a `SUPER_ADMIN` does not bypass them.

Reassignment returns the request to `ASSIGNED` even from `IN_PROGRESS` or `ON_HOLD`, so the new
worker starts the work again. This is the matrix rule, not an invention of this slice.

### Valid assignment target

The named `workerId` must exist, or the response is `404 WORKER_NOT_FOUND`. An existing user that
does not hold the `WORKER` role, or whose status is not `ACTIVE`, is `409 INVALID_WORKER`.

`INVALID_WORKER` is a `409` rather than a `400` because the payload is well formed and the failure
is the referenced entity state, which is how the creation slice already maps a retired facility or
an inactive category. `400` stays reserved for malformed input and failed field validation.

V1 does not restrict which dormitory a worker may be assigned to. `admin_dormitory_scopes` exists
for administrators only, by the accepted ADR-014 decision, and no worker-to-dormitory mapping
exists. Any `ACTIVE` `WORKER` is assignable to any request the actor is authorized to assign.

### Already assigned to that worker, and the active-assignment race

Assigning the request to the user who is already its active assignee is
`409 REQUEST_ALREADY_ASSIGNED`. Treating it as a silent no-op would be worse than rejecting it: it
would append a misleading history row and would reset an `IN_PROGRESS` request to `ASSIGNED`,
discarding the fact that work had started.

A concurrent command that loses the `ux_assignment_active_request` race is also
`409 REQUEST_ALREADY_ASSIGNED`. The frozen contract explicitly forbids folding every integrity
violation into `VERSION_CONFLICT`, and this index violation means precisely that an active
assignment already exists. `VERSION_CONFLICT` remains reserved for a stale submitted `version` and
for an optimistic-locking failure on the request row.

### Version and atomicity

The submitted `version` is compared against the request before the command executes; a mismatch is
`409 VERSION_CONFLICT`. A flush-time optimistic-locking failure is also `409 VERSION_CONFLICT`.

One application transaction performs the whole command: set `unassigned_at` and `unassigned_by_id`
on the active assignment when reassigning, insert the new assignment row, move the request status
to `ASSIGNED`, and append exactly one append-only RequestHistory row. No network call happens
inside that transaction. A failure anywhere rolls back all of it, so a request never gains an
assignment without its history row.

### Audit rows

Initial assignment appends event type `REQUEST_ASSIGNED`; reassignment appends
`REQUEST_REASSIGNED`. Both record `actor_id` as the acting administrator, `previous_status`, and
`new_status=ASSIGNED`. `metadata` carries `{"workerId": <id>}`, and reassignment also carries
`"previousWorkerId"`. Two distinct event types keep the audit readable and let the check constraint
enumerate exactly what the slice writes.

`reason`, when supplied, is at most 500 characters and is stored on the new assignment row. An
ended row keeps the reason it was created with and gains `unassigned_by_id`.

### V7 migration

V7 creates `maintenance_assignment` exactly as the ERD specifies, with all four user and request
foreign keys `ON DELETE RESTRICT`, `assigned_at` `NOT NULL DEFAULT CURRENT_TIMESTAMP`, the partial
unique index `ux_assignment_active_request`, and an index on `(worker_id, assigned_at DESC)` to
serve `assigned-to-me`. V7 also replaces `ck_request_history_event_type` so it admits
`REQUEST_CREATED`, `REQUEST_ASSIGNED` and `REQUEST_REASSIGNED`. V6 is not edited; the constraint is
dropped and recreated in the new migration.

### Effect on the read contract

`assigneeId` in both ADR-020 read DTOs is the `worker_id` of the active assignment, meaning the row
with `unassigned_at IS NULL`, and is `null` when the request has none. `WORKER` read visibility
matches any assignment row for the caller, ended or active, and `assigned-to-me` with
`active=false` selects the ended ones. The assignment table stays the single source of truth; the
request row carries no denormalized assignee column.

## Contract synchronization

On approval, synchronize the assignment DTO, actor rule, state table, target-validity rule, the
`REQUEST_ALREADY_ASSIGNED` and `INVALID_WORKER` mappings, the `VERSION_CONFLICT` boundary, and the
two history event types into the frozen API specification and permission matrix, record the V7
physical rules in the ERD notes, and refresh the frozen baseline hash. Implementation follows in
the same explicitly requested slice as the ADR-020 read endpoints.

## Alternatives

### 1. Separate endpoints for assign and reassign

Two paths would make each command's precondition explicit in the URL. The frozen inventory defines
one path for both, and the current status already selects the behavior unambiguously. Rejected to
avoid changing a frozen path.

### 2. Denormalize an `assignee_id` column onto `maintenance_request`

This makes the read slice a plain column read instead of a join against the active assignment, and
would simplify the listing query. It was rejected because it creates a second source of truth for
assignment that the partial unique index does not protect, and divergence between the column and
the assignment history would be silent. The cost is that every read projecting `assigneeId` joins
or sub-selects the active row, which the partial unique index supports.

### 3. Map the active-assignment race to `VERSION_CONFLICT`

This needs no second conflict code. The frozen contract names `REQUEST_ALREADY_ASSIGNED` and
explicitly warns against mapping every integrity violation to `VERSION_CONFLICT`, and the two
conditions are genuinely different: one is a stale caller, the other is a competing assignment.

### 4. Map `INVALID_WORKER` to `400`

A non-worker target could be read as a bad field value. It was rejected for consistency: the
creation slice already answers `409` when a well-formed reference is in an unusable state, and
`400` would make the same class of failure answer differently across two slices.

### 5. Treat assignment to the current assignee as an idempotent success

This is friendlier to a retrying client. It was rejected because it would reset `IN_PROGRESS` or
`ON_HOLD` to `ASSIGNED` and append a history row describing a change that did not happen.

### 6. Require a worker-to-dormitory mapping before assignment

This would prevent assigning a worker to a dormitory they do not serve. No such table exists,
ADR-014 deliberately limited scope rows to administrators, and inventing the mapping here would
expand V1 beyond the approved scope. A later slice can add it without changing this command.

## Consequences

This slice adds the assignment aggregate, the first lifecycle transition beyond creation, and the
first use of the frozen version-checked command protocol. It unblocks the accepted ADR-020 read
contract in full, so the read endpoints and the assignment command are implemented together.

The assignment table as single source of truth means `assigneeId` costs a join on every read
projection. Two history event types mean the V6 check constraint must be replaced in V7, and every
later lifecycle slice will extend it the same way.

Start, hold, resume, resolve, close, reopen, reject, mark-duplicate, visits, work logs, attachments
and comments stay out of scope. `WORKER_NOT_ASSIGNED` is named in the frozen error table for those
worker commands and is not used by this slice.
