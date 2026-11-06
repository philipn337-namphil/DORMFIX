# ADR-020: MaintenanceRequest query contract

Status: Accepted - V1 authoritative decision

Date: 2026-11-04

Human approval on 2026-11-04 accepted this contract as proposed, including the `404` response for
out-of-visibility detail reads and the `reporterId` anchor for resident visibility.

Implementation dependency: the `WORKER` visibility rule, `GET /maintenance-requests/assigned-to-me`,
the `assigneeId` filter, and the `assigneeId` read field all require assignment rows. V6 creates
only `maintenance_request` and `request_history`, so no assignment table or `assignee_id` column
exists yet. This contract is therefore implemented together with the assignment slice rather than
before it; the assignment schema and semantics are decided in ADR-021.

## Context

The frozen V1 API inventory already lists three MaintenanceRequest read paths: `GET
/maintenance-requests` (scoped listing), `GET /maintenance-requests/assigned-to-me` (current
worker listing), and `GET /maintenance-requests/{id}` (scoped detail). The permission matrix
states the role intent: a `RESIDENT` creates and reads their own request, an `ADMIN` reads every
request in their dormitory scope, a `SUPER_ADMIN` reads globally, and a former `WORKER` keeps
historical read-only access.

The frozen contract stops there. It does not specify:

1. which identity anchors `RESIDENT` visibility - the reporter, or the caller's current
   Residence room;
2. whether a detail read of a request outside the caller's visibility is `403` or `404`;
3. the list filter parameter set, and whether at least one filter is mandatory;
4. pagination bounds, defaults, and sort order;
5. the exact read DTO fields and the summary-versus-detail split;
6. whether detail embeds RequestHistory, Visits, WorkLogs, comments, or attachments;
7. what `assigned-to-me` means for an assignment that has already ended;
8. the behavior for a scope-less `ADMIN` and for an explicitly out-of-scope filter value.

The frozen API specification defers these on purpose: future paged queries are required to use
explicit DTOs, stable sorting and bounded size, while query/filter fields and public history
projections are left to slice contracts. These items decide authorization boundaries, information
disclosure, and index requirements, so they must not be inferred from implementation convenience.

## Decision

Use a visibility-anchored, bounded, fixed-order read contract for V1.

### Read visibility

| Role | Visible requests | Anchor |
|---|---|---|
| RESIDENT | requests the caller filed | `reporterId` equals the JWT subject |
| WORKER | requests the caller is or was assigned to | any assignment row for the caller, current or ended |
| ADMIN | requests whose room resolves to a dormitory in the caller's scope | `admin_dormitory_scopes`, resolved Space to Building to Dormitory |
| SUPER_ADMIN | all requests | global |

`RESIDENT` visibility is anchored on `reporterId`, not on the caller's current Residence room. A
resident who moves keeps access to the requests they filed, which their frozen `close` and
`reopen` rights depend on, and a new occupant does not inherit the previous occupant's reports.

`WORKER` visibility covers ended assignments, which is what the frozen former-worker
historical-read-only rule requires. Dormitory scope resolution reuses the existing
`DormitoryScopeResolutionService` rather than a second traversal. When a caller holds several
roles, visibility is the union of those rows.

### Out-of-visibility detail reads return 404

`GET /maintenance-requests/{id}` returns `404 REQUEST_NOT_FOUND` both when the request does not
exist and when it exists outside the caller's visibility. Request IDs are sequential and
guessable, so a `403` on this path would turn it into an existence oracle: any authenticated
resident could walk IDs and learn how many requests exist and which are live.

This refines, and does not contradict, the matrix sentence that a non-owner is `403`. That rule
governs commands, where the actor is acting on a resource whose existence the system has already
disclosed to them. Every command keeps `403 ACCESS_DENIED`.

The `404` rule applies only to per-ID detail reads. Endpoint-level role and configuration errors
stay `403 ACCESS_DENIED`, because they are not existence probes: a non-`WORKER` calling
`assigned-to-me`, a scope-less `ADMIN` calling the list, and an explicit `dormitoryId`,
`buildingId`, or `spaceId` filter outside an `ADMIN` scope. This matches the Residence precedent
for list endpoints.

### Listing, filters, pagination and ordering

`GET /maintenance-requests?status=&priority=&categoryId=&dormitoryId=&buildingId=&spaceId=&assigneeId=&page=&size=`

- `status` is repeatable; the others are single-valued. All are optional.
- No filter is mandatory. This differs from the admin Residence list because read visibility
  already bounds every result set and `size` is capped, while a request list is an
  administrator's primary work queue and must be usable unfiltered.
- Filters narrow within visibility and never widen it. A filter naming a resource the caller
  cannot see yields an empty page, except the scope cases above, which are `403`.
- An unparsable `status` or `priority` value is `400 VALIDATION_FAILED`.
- `page` is zero-based with default `0`. `size` is `1..100` with default `20`. A violation is
  `400 VALIDATION_FAILED`, consistent with the existing Residence pagination guard.
- Ordering is fixed at `createdAt DESC, id DESC`. V1 exposes no client sort parameter. The
  ordering must be expressed so that Spring Data cannot append Java property names to it; the
  Residence slice already had to fix exactly that defect in native ordering.

`GET /maintenance-requests/assigned-to-me` is `WORKER`-only and returns the same page envelope,
DTO, bounds and ordering. An `active` parameter defaults to `true` and selects the caller's
current assignment rows (`unassigned_at IS NULL`); `active=false` selects ended assignments,
which is how a former assignee reaches their historical read-only view.

### Read DTOs

The listing returns a summary projection: `id`, `requestNumber`, `status`, `priority`,
`categoryId`, `spaceId`, `facilityId`, `title`, `assigneeId`, `createdAt`, `updatedAt`,
`version`.

The detail returns the summary fields plus `reporterId`, `description`, `entryPolicy`,
`contactBeforeEntry`, `preferredVisitStart`, `preferredVisitEnd`, `duplicateOfId`, and the
lifecycle timestamps persisted by V6.

`description` and the entry fields are deliberately absent from the summary. They make list
payloads unbounded and they broadcast resident entry-consent data across bulk listings, which the
matrix treats as resident-owned.

Detail embeds no RequestHistory, Visit, WorkLog, comment, or attachment collection. Each has its
own frozen path and its own visibility rule - notably that a resident never sees a `STAFF_ONLY`
comment. Embedding them would duplicate those rules inside the request read path, which is
precisely where a disclosure defect would be most costly, and would make the detail payload
unbounded.

The page envelope reuses the existing shape: `content`, `page`, `size`, `totalElements`,
`totalPages`. `version` is always exposed, because the frozen contract requires the request read
DTO to expose it for the version-checked commands.

### Boundary and transactions

Reads run in read-only transactions. Responses use explicit records; no managed JPA entity is
serialized at the HTTP boundary. Controllers call application query services only and own no
transaction or repository access. Visibility predicates are evaluated in the query, not by
filtering a fully loaded page in memory, so that `totalElements` is correct.

### Errors

`404 REQUEST_NOT_FOUND`, `403 ACCESS_DENIED`, and `400 VALIDATION_FAILED` are all existing frozen
codes. This contract introduces no new error code.

## Contract synchronization

On approval, synchronize the read DTOs, filter set, pagination bounds, fixed ordering, visibility
anchors, the read-versus-command `404` and `403` rule, and the error mapping into the frozen API
specification and permission matrix, then refresh the frozen baseline hash. Java implementation is
deferred to a separately requested slice.

## Alternatives

### 1. Return 403 for out-of-visibility detail reads

This matches the Residence precedent and the literal matrix sentence, and keeps one rule for
reads and commands. It was rejected because request IDs are sequential, which makes `GET /{id}`
an existence and volume oracle for any authenticated user. The chosen split keeps `403` wherever
the caller already knows the resource exists.

### 2. Anchor RESIDENT visibility on the current Residence room

This would let a new occupant see outstanding problems in the room they moved into, which has
real operational value. It was rejected because it silently revokes a mover's access to requests
they filed, breaks the frozen `close` and `reopen` rights attached to those requests, and
discloses a previous occupant's reports. A later explicit room-history path can serve the
operational need without widening resident visibility now.

### 3. Accept a client-controlled sort parameter

This is flexible for a UI. It needs an allow-list, index coverage for every sort key, and it
makes a later move to keyset pagination harder. V1 fixes one stable order, matching Residence.

### 4. Embed history, visits and comments in the detail response

This serves a detail screen in one round trip. It was rejected because it copies each
sub-resource visibility rule into the request read path and makes the payload unbounded.

### 5. Require at least one filter on the list, as the admin Residence list does

This bounds accidental wide scans. It was rejected because the request list is the
administrator's work queue, visibility already bounds it, and `size` is capped at 100.

## Consequences

The read slice gains a narrow, testable visibility boundary that reuses existing scope resolution
and the existing pagination and page-envelope conventions, and it adds no error code. Fixed
ordering and the summary projection keep list cost predictable and make the required indexes
explicit: lookups by `reporter_id`, by assignment, and by resolved dormitory, each ordered by
`created_at DESC, id DESC`.

Choosing `404` for out-of-visibility detail reads means the read path and the command paths answer
differently for the same request, which the implementation must cover with tests for both.

This decision authorizes contract-conforming implementation in a later explicitly requested slice.
It does not itself add Java, Flyway, or frozen product changes, and assignment, lifecycle, visit,
attachment and comment behavior stay out of scope.
