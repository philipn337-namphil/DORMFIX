# ADR-018: Residence management contract

Status: Accepted - V1 authoritative decision

Date: 2026-10-01

## Decision

Residence is an independent, immutable-history aggregate. It references `User` and `Space` by
ID only. A period is `[startDate, endDate)`: `startDate` is inclusive and a non-null `endDate` is
the first local date on which the resident no longer lives in the room. It is current on date `D`
when `startDate <= D` and (`endDate` is null or `D < endDate`). `D` is calculated in the
referenced room's Dormitory timezone; values are stored as `DATE`.

Creation supports current residence only. `CreateResidenceRequest` is exactly
`residentId`, `roomSpaceId`, and `startDate`; it creates `endDate = null`. `startDate` can be
today or earlier, but never later. V1 has no direct creation of closed historical rows, no generic
PATCH, no hard delete, and no atomic move endpoint. End is only the named command below.

There can be no overlapping periods for one resident. Multiple residents can occupy one ROOM at
the same time because V1 has no capacity contract. An ended Residence is immutable. Ending sets
`endDate` to the current Dormitory date and requires `startDate < today`; same-day creation cannot
be ended on the same date. A same-day move is permitted as two non-atomic commands: end an older
current Residence, then create a new current Residence starting today.

The target user must have RESIDENT role. The target Space must be an active ROOM below an active
Building and Dormitory. Residence does not provide any MaintenanceRequest or entry-consent change.

## API contract

All responses are DTOs with `id`, `residentId`, `roomSpaceId`, `startDate`, `endDate`, and
`createdAt`. `ResidencePageResponse` has `content`, `page`, `size`, `totalElements`, and
`totalPages`.

| Method | Path | Request/result | Authorization |
|---|---|---|---|
| POST | `/api/v1/admin/residences` | `CreateResidenceRequest(residentId, roomSpaceId, startDate)`; 201 ResidenceResponse | scoped ADMIN for the room, or SUPER_ADMIN |
| GET | `/api/v1/admin/residences/{residenceId}` | ResidenceResponse | scoped ADMIN for the Residence room, or SUPER_ADMIN |
| GET | `/api/v1/admin/residences?residentId=&roomSpaceId=&current=&page=&size=` | ResidencePageResponse | scoped ADMIN or SUPER_ADMIN |
| POST | `/api/v1/admin/residences/{residenceId}/end` | empty command; 200 ResidenceResponse | scoped ADMIN for the Residence room, or SUPER_ADMIN |
| GET | `/api/v1/me/residences?current=&page=&size=` | own ResidencePageResponse | authenticated RESIDENT only |

Admin list requires one or more of `residentId`, `roomSpaceId`, and `current`; `size` is 1–100,
and order is `startDate DESC, id DESC`. SUPER_ADMIN sees all results. ADMIN sees only rooms in its
Dormitory scopes. An explicit out-of-scope `roomSpaceId` is 403; scope-less ADMIN is 403; records
outside scope are never included. The self endpoint has no user selector. WORKER can use it only
when the actual role set also contains RESIDENT.

| Status | Meaning |
|---|---|
| 400 | malformed DTO/date, unknown create field such as `endDate`, missing filter, or invalid pagination |
| 403 | missing role, required dormitory scope, or self relationship |
| 404 | Residence, User, Space, or referenced hierarchy record is absent |
| 409 | overlapping period, non-current/same-day end, non-ROOM, or inactive hierarchy |

## Persistence and concurrency

Flyway owns the table and uses BIGINT identity keys, `DATE` period columns, TIMESTAMPTZ
`created_at`, and restrictive foreign keys. PostgreSQL `btree_gist` and an exclusion constraint
over `resident_id WITH =` and `daterange(start_date, end_date, '[)') WITH &&` are the race-safe
final defence against overlap. Application validation gives readable errors and maps that named
constraint to `409 RESIDENCE_PERIOD_OVERLAP`.

## Consequences

Controllers map DTOs only; the application service owns each command transaction; no other
aggregate graph, generic status endpoint, or Phase 3 workflow is introduced.
