# API v1.0 - frozen contract

이 문서는 DormFix V1 API의 authoritative frozen contract이다. 아래 endpoint, command, version, error 의미와 S3 presigned 흐름은 변경하지 않는다.

Base /api/v1. Bearer token authentication. Resource reads/metadata updates use REST; lifecycle changes use explicit commands. This is a contract inventory, not a claim that endpoints are implemented. DTO details not specified below require review in the owning feature.

## Endpoints

All paths below include the base. `{id}` identifies a request unless another name is shown.

| Method | Path | Purpose |
|---|---|---|
| POST | /auth/signup | Sign up |
| POST | /auth/login | Log in |
| POST | /auth/refresh | Refresh access |
| POST | /auth/logout | Log out |
| GET | /me | Current user |
| GET | /dormitories | Dormitories |
| GET | /dormitories/{dormitoryId}/buildings | Buildings |
| GET | /buildings/{buildingId}/spaces | Spaces |
| GET | /spaces/{spaceId} | Space detail |
| GET | /spaces/{spaceId}/facilities | Facilities |
| GET | /categories | Categories |
| POST | /maintenance-requests | Create |
| GET | /maintenance-requests | Scoped listing |
| GET | /maintenance-requests/assigned-to-me | Current worker listing |
| GET | /maintenance-requests/{id} | Scoped detail |
| PATCH | /maintenance-requests/{id} | Allowed metadata only, version required |
| POST | /maintenance-requests/{id}/assignments | Initial assignment/reassignment |
| POST | /maintenance-requests/{id}/start | Start |
| POST | /maintenance-requests/{id}/hold | Hold |
| POST | /maintenance-requests/{id}/resume | Resume |
| POST | /maintenance-requests/{id}/resolve | Resolve |
| POST | /maintenance-requests/{id}/close | Confirm/close |
| POST | /maintenance-requests/{id}/reopen | Reopen RESOLVED |
| POST | /maintenance-requests/{id}/reject | Reject REPORTED |
| POST | /maintenance-requests/{id}/mark-duplicate | Mark REPORTED duplicate |
| POST | /maintenance-requests/{id}/visits | Schedule visit |
| GET | /maintenance-requests/{id}/visits | List visits |
| PATCH | /visits/{visitId} | Manage allowed visit fields |
| POST | /visits/{visitId}/complete | Complete visit only |
| POST | /visits/{visitId}/no-access | Record no access |
| POST | /visits/{visitId}/cancel | Cancel visit |
| POST | /maintenance-requests/{id}/work-logs | Append WorkLog |
| GET | /maintenance-requests/{id}/work-logs | Read permitted WorkLogs |
| POST | /maintenance-requests/{id}/comments | Add comment |
| GET | /maintenance-requests/{id}/comments | Read permitted comments |
| DELETE | /comments/{commentId} | Soft-delete under reviewed policy |
| POST | /maintenance-requests/{id}/attachments/presigned-url | Authorize upload |
| POST | /maintenance-requests/{id}/attachments | Register uploaded metadata |
| GET | /maintenance-requests/{id}/attachments | List allowed metadata |
| GET | /attachments/{attachmentId}/download-url | Authorize private download |
| GET | /maintenance-requests/{id}/history | Read permitted history |
| GET | /notifications | Own notifications |
| PATCH | /notifications/{id}/read | Mark read |
| POST | /notifications/read-all | Mark own notifications read |
| GET | /notifications/unread-count | Own unread count |

### Maintenance request creation

`POST /maintenance-requests` is an authenticated `RESIDENT`-only command. Its request DTO is
exactly `CreateMaintenanceRequest(spaceId, facilityId?, categoryId, title, description,
entryPolicy, contactBeforeEntry, preferredVisitStart?, preferredVisitEnd?)`. It never accepts
`reporterId`, `status`, `priority`, `duplicateOfId`, lifecycle timestamps, or `version`.
`reporterId` is always the JWT subject.

`spaceId` must be the caller's current Residence ROOM; V1 does not permit a resident to report a
common area or another resident's room. The Space and its Building/Dormitory hierarchy, and the
category, must be active. When supplied, `facilityId` must belong to `spaceId` and cannot be
`RETIRED`; an `OUT_OF_SERVICE` facility remains reportable. The server sets `status=REPORTED`,
`priority=category.defaultPriority`, `duplicateOfId=null`, timestamps, and the initial version.

Preferred visit timestamps are both absent or both present, and when present
`preferredVisitStart < preferredVisitEnd`; they are a preference, not a scheduled Visit. After
the database ID is allocated, the server sets the stable public `requestNumber` to `DF-<id>`.
Creation returns `201 Created`, `Location: /api/v1/maintenance-requests/{id}`, and the normal
request response including `id`, `requestNumber`, `status`, `priority`, and `version`.

Malformed input, an invalid `EntryPolicy`, or an invalid preferred-time range is `400`. A missing
referenced resource is `404`. A Space outside the caller's current Residence, inactive Space
hierarchy/category, a facility outside the submitted Space, or a retired facility is `409
INVALID_REQUEST_STATE`.

### Maintenance request reads

Read visibility is anchored per role. A `RESIDENT` sees the requests they filed, matched on
`reporterId`, and keeps them after moving rooms. A `WORKER` sees every request they are or were
assigned to, including ended assignments. An `ADMIN` sees requests whose room resolves to a
dormitory in their scope. A `SUPER_ADMIN` sees all requests. A caller holding several roles sees
the union.

`GET /maintenance-requests/{id}` returns `404 REQUEST_NOT_FOUND` both when the request does not
exist and when it exists outside the caller visibility, so the path does not disclose the
existence of requests the caller may not read. Commands keep `403 ACCESS_DENIED` for a wrong
role, a non-owner, or a former assignee. Endpoint-level role and configuration failures also stay
`403 ACCESS_DENIED`: a non-`WORKER` calling `assigned-to-me`, a scope-less `ADMIN` calling the
listing, and an explicit `dormitoryId`, `buildingId` or `spaceId` filter outside an `ADMIN` scope.

`GET /maintenance-requests?status=&priority=&categoryId=&dormitoryId=&buildingId=&spaceId=&assigneeId=&page=&size=`
returns `MaintenanceRequestPageResponse`. `status` is repeatable; the remaining filters are
single-valued; all are optional and no filter is mandatory. Filters narrow within visibility and
never widen it, so a filter naming an invisible resource yields an empty page except the `403`
scope cases above. `page` is zero-based with default `0` and `size` is `1..100` with default `20`;
a violation or an unparsable `status`/`priority` is `400 VALIDATION_FAILED`. Ordering is fixed at
`createdAt DESC, id DESC` and V1 exposes no client sort parameter.

`GET /maintenance-requests/assigned-to-me` is `WORKER`-only and uses the same page envelope, DTO,
bounds and ordering. `active` defaults to `true` and selects current assignment rows; `active=false`
selects ended assignments, which is how a former assignee reaches historical read-only access.

The listing item DTO is `MaintenanceRequestSummaryResponse(id, requestNumber, status, priority,
categoryId, spaceId, facilityId, title, assigneeId, createdAt, updatedAt, version)`. The detail DTO
adds `reporterId`, `description`, `entryPolicy`, `contactBeforeEntry`, `preferredVisitStart`,
`preferredVisitEnd`, `duplicateOfId` and the persisted lifecycle timestamps. `description` and the
entry fields are absent from the listing projection so bulk listings do not broadcast resident
entry-consent data. Detail embeds no RequestHistory, Visit, WorkLog, comment or attachment
collection; each has its own path and its own visibility rule. The page envelope is `content`,
`page`, `size`, `totalElements`, `totalPages`, and both read DTOs always expose `version`.

### Maintenance request assignment

`POST /maintenance-requests/{id}/assignments` takes exactly
`AssignWorkerRequest(workerId, reason?, version)`. It never accepts `status`, `assignedById`,
`assignedAt`, `unassignedAt` or an assignment identifier, and `assignedById` is always the JWT
subject. `reason` is at most 500 characters and is stored on the new assignment row.

The actor is an `ADMIN` whose scope contains the dormitory that the request room resolves to, or a
`SUPER_ADMIN`. A wrong role, a scope-less `ADMIN`, or an `ADMIN` outside the request dormitory is
`403 ACCESS_DENIED`; the read-path `404` rule does not apply to commands.

One path serves both behaviors and the current status selects which. From `REPORTED` it is an
initial assignment. From `ASSIGNED`, `IN_PROGRESS` or `ON_HOLD` it is a reassignment that ends the
active assignment and creates a new one, and the request returns to `ASSIGNED` so the new worker
starts again. From `RESOLVED`, `CLOSED`, `REJECTED` or `DUPLICATE` it is
`409 INVALID_REQUEST_STATE`; a `SUPER_ADMIN` does not bypass the terminal states.

A `workerId` that does not exist is `404 WORKER_NOT_FOUND`. An existing user that does not hold the
`WORKER` role, or whose status is not `ACTIVE`, is `409 INVALID_WORKER`, because the payload is well
formed and the failure is the referenced entity state. Assigning the request to the user who is
already its active assignee is `409 REQUEST_ALREADY_ASSIGNED`, and a concurrent command that loses
the active-assignment unique index race returns the same code. `409 VERSION_CONFLICT` is reserved
for a stale submitted `version` and for an optimistic-locking failure on the request row, and is
never used for the active-assignment violation.

The command is one transaction: end the active assignment when reassigning, insert the new
assignment, move the status to `ASSIGNED`, and append exactly one RequestHistory row. Initial
assignment appends event type `REQUEST_ASSIGNED` and reassignment appends `REQUEST_REASSIGNED`;
both record the acting administrator, the previous status, `new_status=ASSIGNED`, and `metadata`
carrying `workerId` plus `previousWorkerId` on reassignment.

`MaintenanceAssignment` is the single source of truth for assignment. The request row carries no
denormalized assignee column, and `assigneeId` in the read DTOs is the `worker_id` of the row whose
`unassigned_at` is null, or `null` when the request has none. V1 does not restrict which dormitory a
worker may be assigned to, because `admin_dormitory_scopes` covers administrators only and no
worker-to-dormitory mapping exists.

### Maintenance request lifecycle commands

Six commands take `LifecycleCommandRequest(statusReason?, version)`: `start`, `hold`, `resume`,
`resolve`, `close` and `reopen`. `reject` takes the same shape. `mark-duplicate` takes
`MarkDuplicateRequest(duplicateOfId, statusReason?, version)`. `hold` and `reject` require
`statusReason`; elsewhere it is optional. It is at most 500 characters. No command accepts `status`,
`assigneeId`, a lifecycle timestamp, `reporterId` or `priority`, and the actor is the JWT subject.

`status_reason` is a projection of the current status: every transition writes the supplied value,
or `null` when the command carries none. The append-only RequestHistory row keeps the reason of
each transition, so the column is never accumulated.

`resolve` sets `resolved_at` and `close` sets `closed_at`. `reopen` clears `resolved_at` and never
touches `closed_at`, because a `CLOSED` request cannot be reopened. Resolving again after a reopen
overwrites `resolved_at`. `reject` and `mark-duplicate` set neither timestamp.

`mark-duplicate` requires a `duplicateOfId` that exists and is visible to the actor under the read
visibility rules; otherwise the response is `404 REQUEST_NOT_FOUND`, identical to a missing request.
A self-reference, or an original that is itself `DUPLICATE`, is `409 INVALID_REQUEST_STATE`: V1
permits no duplicate chains. `duplicate_of_id` is written once because `DUPLICATE` is terminal.

`close`, `reject` and `mark-duplicate` end the active assignment in the same transaction, setting
`unassigned_at` and `unassigned_by_id` to the acting user, so terminal work leaves the worker
listing. `resolve` does not end it, because `reopen` must return the request to `IN_PROGRESS` with
that worker. `reopen` therefore requires an active assignment and is otherwise
`409 INVALID_REQUEST_STATE`.

`start`, `hold`, `resume` and `resolve` require the caller to be the current active assignee. A
caller holding the `WORKER` role who is not that assignee, including a former assignee, receives
`403 WORKER_NOT_ASSIGNED`; a caller whose role cannot use the command receives
`403 ACCESS_DENIED`. `close` and `reopen` accept a `RESIDENT` whose id equals `reporter_id`, or a
scoped `ADMIN`/`SUPER_ADMIN`. `reject` and `mark-duplicate` accept only a scoped
`ADMIN`/`SUPER_ADMIN`. Authorization is evaluated before any state check.

A source state outside the matrix is `409 INVALID_REQUEST_STATE`; the commands are not idempotent.
One transaction performs the state change, the terminal assignment end where it applies, the reason
and timestamp writes, and exactly one RequestHistory row. The eight commands append
`REQUEST_STARTED`, `REQUEST_HELD`, `REQUEST_RESUMED`, `REQUEST_RESOLVED`, `REQUEST_CLOSED`,
`REQUEST_REOPENED`, `REQUEST_REJECTED` and `REQUEST_MARKED_DUPLICATE`, and the duplicate row carries
`{"duplicateOfId": <id>}` in `metadata`.

### Maintenance visits

`POST /maintenance-requests/{id}/visits` takes `ScheduleVisitRequest(scheduledStart, scheduledEnd,
note?)`. It never accepts `workerId`, `status`, either actual timestamp, `entryPolicySnapshot` or
`contactBeforeEntry`. `worker_id` is the request's current active assignee, resolved by the server,
and `created_by_id` is the JWT subject, so an administrator who schedules a visit is recorded as its
creator while the assigned worker remains the person who performs it.

Scheduling requires an active assignment and a request in `ASSIGNED`, `IN_PROGRESS` or `ON_HOLD`;
otherwise `409 INVALID_REQUEST_STATE`. Both scheduled timestamps are required with
`scheduledStart < scheduledEnd`, otherwise `400 INVALID_VISIT_TIME`. A window in the past is
accepted, because a worker may record a visit that already happened. Creation snapshots
`entry_policy_snapshot` and `contact_before_entry` from the request, sets `status=SCHEDULED`, and
leaves both actual timestamps null.

A request has at most one `SCHEDULED` visit, reinforced by a partial unique index on `request_id`
where the status is `SCHEDULED`. A second scheduling attempt while one is open is
`409 INVALID_REQUEST_STATE`.

`PATCH /visits/{visitId}` takes `UpdateVisitRequest(scheduledStart?, scheduledEnd?, note?)` and
rejects unknown fields. It can never change `status`, `workerId`, `requestId`, either actual
timestamp, `entryPolicySnapshot` or `contactBeforeEntry`. Only a `SCHEDULED` visit may be patched,
otherwise `409 INVALID_REQUEST_STATE`. The two scheduled timestamps are supplied together or not at
all and must satisfy `scheduledStart < scheduledEnd`, otherwise `400 INVALID_VISIT_TIME`. A patch
that changes the window appends `VISIT_RESCHEDULED`; a note-only patch appends nothing.

`SCHEDULED` is the only source state for the three outcome commands and each outcome is terminal
for the visit. None of them changes the request status, because completing a visit is not resolving
the request. `POST /visits/{visitId}/complete` takes
`CompleteVisitRequest(actualStartedAt, actualEndedAt, note?)`, requires both timestamps with
`actualStartedAt < actualEndedAt`, and writes them with `status=COMPLETED`; the server does not
invent them because it cannot know when the worker arrived or left.
`POST /visits/{visitId}/no-access` and `POST /visits/{visitId}/cancel` take
`VisitOutcomeRequest(note)` with `note` required, write no actual timestamps, and set `NO_ACCESS`
or `CANCELED`. `complete` and `no-access` require the request not to be terminal, otherwise
`409 INVALID_REQUEST_STATE`; `cancel` is always available on a `SCHEDULED` visit, which is how a
visit left open when its request was closed is cleared.

Scheduling, patching and all three outcome commands are performed by the request's current active
assignee or by a scoped `ADMIN`/`SUPER_ADMIN`. A caller holding `WORKER` who is not the current
assignee receives `403 WORKER_NOT_ASSIGNED`; a caller whose role cannot use the command receives
`403 ACCESS_DENIED`. Authorization is evaluated before the state checks. A missing visit is
`404 VISIT_NOT_FOUND`.

`GET /maintenance-requests/{id}/visits` reuses the request read visibility, so a request outside
visibility answers `404 REQUEST_NOT_FOUND` exactly as the detail read does. A `RESIDENT` does not
receive `note`, which is free-text staff content written at completion, cancellation or no-access;
everything else, including the consent snapshot that is the resident's own, is visible to them. The
list is ordered `scheduledStart DESC, id DESC` and is not paginated.

Visit commands are not version-checked in V1: `MaintenanceVisit` has no `version` column, and the
`SCHEDULED`-only precondition with the open-visit index makes a lost update answer `409`. The five
audit events are `VISIT_SCHEDULED`, `VISIT_RESCHEDULED`, `VISIT_COMPLETED`, `VISIT_CANCELED` and
`VISIT_NO_ACCESS`, each carrying `{"visitId": <id>}` in `metadata`.

### Maintenance work logs

`POST /maintenance-requests/{id}/work-logs` takes
`AppendWorkLogRequest(actionType, description, visitId?)`. It never accepts `workerId`, `createdAt`
or `id`; `worker_id` is the JWT subject. `actionType` is one of the frozen `WorkActionType` values
and an unparsable value is `400 VALIDATION_FAILED`. `description` is required and at most 2000
characters.

A supplied `visitId` must belong to the same request, otherwise `409 INVALID_REQUEST_STATE`; a
missing visit is `404 VISIT_NOT_FOUND`. The visit status is not restricted, because a work log may
describe a completed visit, a failed entry, or a cancelled attempt equally.

Only the request's current active assignee appends a work log. A caller holding `WORKER` who is not
that assignee receives `403 WORKER_NOT_ASSIGNED` and a role that cannot use the command receives
`403 ACCESS_DENIED`. `ADMIN` and `SUPER_ADMIN` do not append work logs: `worker_id` means the person
who did the work, and an administrator's remark belongs in a comment. The request must not be
terminal, otherwise `409 INVALID_REQUEST_STATE`.

`GET /maintenance-requests/{id}/work-logs` is staff-only. A `WORKER` with any assignment row for the
request, current or ended, a scoped `ADMIN`, and a `SUPER_ADMIN` may read; a `RESIDENT` receives
`403 ACCESS_DENIED`. Work logs are the internal record of the work, and `PUBLIC` comments are the
resident-facing channel, which is the same line that keeps a `STAFF_ONLY` comment away from a
resident. A request outside the caller's read visibility is `404 REQUEST_NOT_FOUND` as elsewhere;
a `RESIDENT` reading their own request receives `403` instead, because they already know it exists.

The list returns `WorkLogPageResponse` ordered `createdAt ASC, id ASC`, because a work log is a
narrative that reads oldest first, unlike the visit list. `page` is zero-based with default `0` and
`size` is `1..100` with default `20`; a violation is `400 VALIDATION_FAILED`. The item DTO is
`WorkLogResponse(id, requestId, visitId, workerId, actionType, description, createdAt)`.

Appending a work log writes no RequestHistory row and does not modify the request: no status, no
`updated_at`, no `version`, and no version check. Work logs are append-only with no update or
delete path.

### Maintenance request metadata PATCH

`PATCH /maintenance-requests/{id}` uses two allow-listed DTOs on one path, selected by the role of
the caller. A caller holding `ADMIN` or `SUPER_ADMIN` sends
`AdminUpdateMaintenanceRequest(categoryId?, priority?, facilityId?, version)`; a caller holding only
`RESIDENT` sends `ResidentUpdateMaintenanceRequest(title?, description?, entryPolicy?,
contactBeforeEntry?, preferredVisitStart?, preferredVisitEnd?, version)`. The two field sets do not
intersect, and a field from the other set is an unknown field, `400 VALIDATION_FAILED`. A caller
holding both a resident and an administrator role is interpreted as an administrator. No role can
change `status`, `reporterId`, `requestNumber`, `spaceId`, `duplicateOfId`, `statusReason`, a
lifecycle timestamp or anything about assignment through this path.

A `RESIDENT` may patch only a request they reported and only while it is `REPORTED`; a request
reported by someone else is `403 ACCESS_DENIED` and any other status is
`409 INVALID_REQUEST_STATE`. An administrator may patch while the request is `REPORTED`, `ASSIGNED`,
`IN_PROGRESS` or `ON_HOLD`; `RESOLVED` and the terminal states are `409 INVALID_REQUEST_STATE`. An
`ADMIN` needs the dormitory of the request in scope, otherwise `403 ACCESS_DENIED`. Authorization is
evaluated before state checks.

`spaceId` is never patched: creation anchors it to the current Residence room of the reporter, and
moving it afterwards would reduce that rule to a creation-time formality. `facilityId` may be set,
replaced, or cleared with an explicit `null`; a new value must belong to the space of the request
and must not be `RETIRED`, otherwise `409 INVALID_REQUEST_STATE`, and a missing facility is
`404 FACILITY_NOT_FOUND`. A new `categoryId` must be active, otherwise
`409 INVALID_REQUEST_STATE`, and a missing category is `404 CATEGORY_NOT_FOUND`. Changing
`categoryId` never resets `priority` to the new category default.

Only fields present in the body change. `facilityId`, `preferredVisitStart` and
`preferredVisitEnd` accept an explicit `null` to clear; `null` for any other field is
`400 VALIDATION_FAILED`. After the patch the preferred visit timestamps must be both absent or both
present with `preferredVisitStart < preferredVisitEnd`, otherwise `400 INVALID_VISIT_TIME`. `title`
is at most 150 characters and `description` at most 2000.

`version` is required; a mismatch or an optimistic-locking failure is `409 VERSION_CONFLICT`. A
patch that changes a value increments `version`, and the response is the request detail DTO. A patch
that changes nothing writes no history, does not increment `version`, and returns the current
state. A changed `categoryId` appends `CATEGORY_CHANGED`, a changed `priority` appends
`PRIORITY_CHANGED`, and any other changed field appends one `REQUEST_METADATA_UPDATED`; a patch that
changes both category and priority appends both rows. `metadata` carries the names of the changed
fields only, never their values, so entry consent and free text are not copied into audit rows.

### Maintenance request comments

`POST /maintenance-requests/{id}/comments` takes `AddCommentRequest(content, visibility)`. It never
accepts `authorId`, `createdAt` or `id`; `author_id` is the JWT subject. `content` is required and
at most 2000 characters. `visibility` is required and has no default, so an internal remark cannot
become public through an omitted field; an unparsable value is `400 VALIDATION_FAILED`.

Write authority follows the relationship of the caller to the request, not the role alone. The
reporter `RESIDENT` may write `PUBLIC`. The current active assignee `WORKER`, an `ADMIN` with the
dormitory of the request in scope, and a `SUPER_ADMIN` may write `PUBLIC` or `STAFF_ONLY`. A caller
without such a staff relationship who sends `STAFF_ONLY` receives `403 ACCESS_DENIED`, including a
reporter who also holds `WORKER` but is not assigned to the request. A former assignee receives
`403 WORKER_NOT_ASSIGNED`. A request outside read visibility is `404 REQUEST_NOT_FOUND`.

A comment may be added while the request is not terminal, which includes `RESOLVED`; on `CLOSED`,
`REJECTED` or `DUPLICATE` it is `409 INVALID_REQUEST_STATE`. Adding a comment does not modify the
request, performs no version check, and writes no RequestHistory row.

`GET /maintenance-requests/{id}/comments` reuses the request read visibility. A reader with a staff
relationship to the request - a `SUPER_ADMIN`, a scoped `ADMIN`, or a `WORKER` with any assignment
row for it, current or ended - receives `PUBLIC` and `STAFF_ONLY` comments. Any other reader, which
is the reporter `RESIDENT`, receives `PUBLIC` only, and `totalElements` and `totalPages` then count
`PUBLIC` comments only, so the number of internal remarks is not disclosed. The filter is applied in
the query. The list returns `CommentPageResponse` ordered `createdAt ASC, id ASC`; `page` is
zero-based with default `0` and `size` is `1..100` with default `20`, and a violation is
`400 VALIDATION_FAILED`. The item DTO is
`CommentResponse(id, requestId, authorId, content, visibility, createdAt)`.

`DELETE /comments/{commentId}` soft-deletes by setting `deleted_at` and returns `204 No Content`.
The author may delete their own comment while they still hold the relationship that lets them
write on the request, so a former assignee cannot. A scoped `ADMIN` or a `SUPER_ADMIN` may delete
any comment. Deletion is not gated by request status. A deleted comment disappears from every
listing and count while its row is retained. Deletion appends a `COMMENT_DELETED` RequestHistory
row carrying the actor and `{"commentId": <id>}` and never the content.

A comment the caller cannot read answers `404 COMMENT_NOT_FOUND` on delete: a missing comment, an
already deleted one, one on a request outside visibility, and a `STAFF_ONLY` comment named by a
resident are indistinguishable, so the path does not disclose internal remarks. A comment the
caller can read but may not delete is `403 ACCESS_DENIED`. There is no comment update path.

### Maintenance request attachments

Storage sits behind an `AttachmentStorage` port that issues upload presigns, reports the actual
size and content type of a stored object, and issues download presigns. This slice ships no real S3
adapter. The default adapter fails closed, and the attachment paths then answer
`503 STORAGE_UNAVAILABLE` in the standard error body, so an unconnected store is visibly absent
rather than pretending that uploads exist. No storage call runs inside a database transaction.

`POST /maintenance-requests/{id}/attachments/presigned-url` takes
`PresignUploadRequest(type, originalFilename, contentType, fileSize)` and returns
`PresignedUploadResponse(objectKey, uploadUrl, method, headers, expiresAt)`. The server generates
the object key as `requests/{requestId}/attachments/{uploaderId}/{uuid}`; the file name is never
part of the key. The presigned URL is bound to the declared content type and size.

Limits are configuration, not product constants. The defaults are a maximum file size of 10 MiB,
the content types `image/jpeg`, `image/png`, `image/webp` and `application/pdf`, at most 20
attachments per request, an upload URL lifetime of 10 minutes and a download URL lifetime of 5
minutes. A content type outside the allow-list is `400 INVALID_FILE_TYPE`; a size above the limit or
not positive is `400 FILE_TOO_LARGE`; a request already at its attachment limit is
`409 INVALID_REQUEST_STATE`. `originalFilename` is required, at most 255 characters, and may not
contain a path separator or a control character, otherwise `400 VALIDATION_FAILED`.

`POST /maintenance-requests/{id}/attachments` takes
`RegisterAttachmentRequest(objectKey, type, originalFilename)` and never accepts a content type or a
size. The server asks the store for the object and records the size and content type the store
reports, then re-checks the limits against those values, so an upload that differs from its presign
declaration is rejected at registration. An object that is not in the store is
`409 INVALID_REQUEST_STATE`. A key whose prefix does not name this request and this caller is
`403 ACCESS_DENIED`, which is what replaces a pending-upload table. An already registered key is
`409 INVALID_REQUEST_STATE`. Registration does not modify the request, performs no version check,
and writes no RequestHistory row.

Write authority follows the relationship of the caller to the request. The reporter `RESIDENT` may
add `REPORT` and `OTHER`; the current active assignee `WORKER` may add `BEFORE_REPAIR`,
`AFTER_REPAIR` and `OTHER`; a scoped `ADMIN` and a `SUPER_ADMIN` may add `OTHER`. A type outside the
relationship is `403 ACCESS_DENIED`, a former assignee is `403 WORKER_NOT_ASSIGNED`, and a request
outside read visibility is `404 REQUEST_NOT_FOUND`. Attachments may be added while the request is
not terminal, which includes `RESOLVED`; otherwise `409 INVALID_REQUEST_STATE`. Both the presign and
the registration check this.

`GET /maintenance-requests/{id}/attachments` reuses the request read visibility, and every reader
of the request sees all of its attachment metadata. The item DTO is
`AttachmentResponse(id, requestId, uploaderId, type, originalFilename, contentType, fileSize,
createdAt)`; `objectKey` is never returned. The list is ordered `createdAt ASC, id ASC` and is not
paginated, because the per-request limit bounds it.

`GET /attachments/{attachmentId}/download-url` returns `DownloadUrlResponse(url, expiresAt)` when
the caller may read the request the attachment belongs to, and otherwise answers
`404 ATTACHMENT_NOT_FOUND` exactly as for a missing attachment. Presigned URLs are never logged.

An object that was uploaded but never registered is an orphan. Unregistered objects become eligible
for deletion 24 hours after creation, the deletion is performed by a storage-side lifecycle job
that reconciles bucket keys against registered `object_key` values, and the application never
deletes an object in V1. There is no attachment delete path and attachment rows are never hard
deleted.

### Structure administration

All structure-management paths use the `/admin` prefix. These endpoints are part of the frozen contract but are not implemented yet.

| Method | Path | Request DTO | Role |
|---|---|---|---|
| POST | /admin/dormitories | `CreateDormitoryRequest(name, address, timezone)` | SUPER_ADMIN |
| PATCH | /admin/dormitories/{dormitoryId} | `UpdateDormitoryRequest(name, address, timezone)` | SUPER_ADMIN |
| POST | /admin/dormitories/{dormitoryId}/deactivate | `DeactivateCommand` | SUPER_ADMIN |
| POST | /admin/dormitories/{dormitoryId}/buildings | `CreateBuildingRequest(name, buildingType)` | SUPER_ADMIN |
| PATCH | /admin/buildings/{buildingId} | `UpdateBuildingRequest(name, buildingType)` | SUPER_ADMIN |
| POST | /admin/buildings/{buildingId}/deactivate | `DeactivateCommand` | SUPER_ADMIN |
| POST | /admin/buildings/{buildingId}/spaces | `CreateSpaceRequest(name, spaceType, floor, capacity)` | SUPER_ADMIN |
| PATCH | /admin/spaces/{spaceId} | `UpdateSpaceRequest(name, spaceType, floor, capacity)` | SUPER_ADMIN |
| POST | /admin/spaces/{spaceId}/deactivate | `DeactivateCommand` | SUPER_ADMIN |
| POST | /admin/spaces/{spaceId}/facilities | `CreateFacilityRequest(name, facilityType, assetCode, installedAt, description)` | scoped ADMIN/SUPER_ADMIN |
| PATCH | /admin/facilities/{facilityId} | `UpdateFacilityRequest(name, facilityType, assetCode, installedAt, description)` | scoped ADMIN/SUPER_ADMIN |
| POST | /admin/facilities/{facilityId}/out-of-service | `FacilityStatusCommand` | scoped ADMIN/SUPER_ADMIN |
| POST | /admin/facilities/{facilityId}/reactivate | `FacilityStatusCommand` | scoped ADMIN/SUPER_ADMIN |
| POST | /admin/facilities/{facilityId}/retire | `FacilityStatusCommand` | scoped ADMIN/SUPER_ADMIN |
| POST | /admin/categories | `CreateMaintenanceCategoryRequest(parentId, code, name, defaultPriority, sortOrder)` | SUPER_ADMIN |
| PATCH | /admin/categories/{categoryId} | `UpdateMaintenanceCategoryRequest(code, name, defaultPriority, sortOrder)` | SUPER_ADMIN |
| POST | /admin/categories/{categoryId}/deactivate | `DeactivateCommand` | SUPER_ADMIN |
| POST | /admin/categories/{categoryId}/reactivate | `ReactivateCommand` | SUPER_ADMIN |
| POST | /admin/residences | `CreateResidenceRequest(residentId, roomSpaceId, startDate)` | scoped ADMIN/SUPER_ADMIN |
| GET | /admin/residences/{residenceId} | `ResidenceResponse` | scoped ADMIN/SUPER_ADMIN |
| GET | /admin/residences?residentId=&roomSpaceId=&current=&page=&size= | `ResidencePageResponse` | scoped ADMIN/SUPER_ADMIN |
| POST | /admin/residences/{residenceId}/end | `EndResidenceCommand` | scoped ADMIN/SUPER_ADMIN |
| GET | /me/residences?current=&page=&size= | own `ResidencePageResponse` history | RESIDENT self |

`Dormitory.name` is not unique in V1; duplicate names do not produce a dedicated 409 conflict. `MaintenanceCategory.parentId` is create-only: it may be supplied in `CreateMaintenanceCategoryRequest`, is absent from `UpdateMaintenanceCategoryRequest`, and no category move endpoint exists in V1. DTOs are allow-listed and must not bind managed entities or arbitrary status fields.

Facility status commands allow `ACTIVE -> OUT_OF_SERVICE -> ACTIVE`, and `ACTIVE` or `OUT_OF_SERVICE -> RETIRED`. `RETIRED` is terminal and cannot be reactivated. Facility status is never changed through PATCH or a generic status endpoint.

Category reactivation is an explicit SUPER_ADMIN command. It returns 409 `INACTIVE_PARENT` when its immediate parent is inactive; parent deactivation does not cascade. Residence uses `[startDate, endDate)` dates. Creation accepts only `residentId`, `roomSpaceId`, and a non-future `startDate`, always creates a current record, and is ended only by the explicit end command. Admin list requires one of `residentId`, `roomSpaceId`, or `current`, has size 1–100, and uses `startDate DESC, id DESC`. Admin operations require the room dormitory scope; residents read only their own history.

Admin group `/admin/...` also includes representative POST/PATCH residences, GET users and GET dashboard summary. Facility management is limited to the ADMIN's dormitory scope; SUPER_ADMIN is global. MaintenanceCategory is V1 global Master Data and is SUPER_ADMIN-only. Do not invent a generic delete endpoint.

Structure administration policy: only SUPER_ADMIN may create, update, or deactivate Dormitory, Building, and Space. An ADMIN with the relevant dormitory scope or a SUPER_ADMIN may create, update, and issue the Facility status commands. MaintenanceCategory creation, update, and deactivation are SUPER_ADMIN-only. Parent deactivation does not automatically propagate to children; creation and reactivation below an inactive parent are denied; operational data is never hard-deleted.

## Commands and concurrency

Request update, assign/reassign, start, hold, resume, resolve, close, reopen, reject and mark duplicate require the submitted entity version, for example `{ "version": 7 }`. Stale submissions or races detected by JPA @Version return 409 with code VERSION_CONFLICT. The request read DTO must expose version when implemented. Do not substitute blind last-write-wins updates.

PATCH /maintenance-requests/{id} is explicitly allowed for metadata under role/state rules. It must never accept arbitrary status. Residents cannot set priority, reporter or assignment. Administrators cannot alter resident entry consent. Use separate allow-listed DTOs/policies rather than binding entities or reflecting arbitrary field maps. Exact patch field matrices are a review item.

## Attachments

Authorize request -> server issues S3 presigned upload URL -> client uploads directly to S3 -> client separately registers attachment metadata. Download URLs also require resource authorization. S3 upload/network activity must not run inside the request's core transaction. See [security design](../architecture/security.md) for engineering validation requirements; limits are not yet product constants.

## Error contract

| HTTP | Meaning | Representative codes |
|---|---|---|
| 400 | Input validation | INVALID_VISIT_TIME, INVALID_ENTRY_POLICY, INVALID_FILE_TYPE, FILE_TOO_LARGE |
| 401 | Authentication missing/invalid | AUTHENTICATION_REQUIRED |
| 403 | Authenticated but wrong role/ownership/current assignment | ACCESS_DENIED, WORKER_NOT_ASSIGNED |
| 404 | Resource does not exist | USER_NOT_FOUND, SPACE_NOT_FOUND, FACILITY_NOT_FOUND, REQUEST_NOT_FOUND, WORKER_NOT_FOUND, VISIT_NOT_FOUND, COMMENT_NOT_FOUND, ATTACHMENT_NOT_FOUND |
| 409 | Business state/concurrency conflict | INVALID_REQUEST_STATE, REQUEST_ALREADY_ASSIGNED, INVALID_FACILITY_STATE, VERSION_CONFLICT |

INVALID_WORKER is also a representative frozen code; exact validation-vs-business-conflict mapping must be specified by assignment API review. Do not map every integrity constraint error to VERSION_CONFLICT.

Standard body fields: timestamp, status, code, message, path, traceId, fieldErrors. Foundation uses an ISO-8601 Instant timestamp, numeric status, stable string code, safe message, request path without query, server-generated traceId and fieldErrors list of {field, message}. Validation errors must not echo rejected secret values. Unexpected errors use safe 500 INTERNAL_ERROR (engineering fallback, not a new lifecycle rule).

Example stale command response:

```json
{"timestamp":"2026-09-10T00:00:00Z","status":409,"code":"VERSION_CONFLICT","message":"The request has changed. Reload and try again.","path":"/api/v1/maintenance-requests/42/start","traceId":"server-generated-id","fieldErrors":[]}
```

Use the [permission/state matrix](permission-state-matrix-v1.md) for command authorization. Future paged queries use explicit DTOs, stable sorting and bounded size; query/filter fields and public history projections require slice contracts. No managed JPA entity may be serialized at the API boundary.
