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
| 404 | Resource does not exist | USER_NOT_FOUND, SPACE_NOT_FOUND, FACILITY_NOT_FOUND, REQUEST_NOT_FOUND, WORKER_NOT_FOUND, VISIT_NOT_FOUND, ATTACHMENT_NOT_FOUND |
| 409 | Business state/concurrency conflict | INVALID_REQUEST_STATE, REQUEST_ALREADY_ASSIGNED, INVALID_FACILITY_STATE, VERSION_CONFLICT |

INVALID_WORKER is also a representative frozen code; exact validation-vs-business-conflict mapping must be specified by assignment API review. Do not map every integrity constraint error to VERSION_CONFLICT.

Standard body fields: timestamp, status, code, message, path, traceId, fieldErrors. Foundation uses an ISO-8601 Instant timestamp, numeric status, stable string code, safe message, request path without query, server-generated traceId and fieldErrors list of {field, message}. Validation errors must not echo rejected secret values. Unexpected errors use safe 500 INTERNAL_ERROR (engineering fallback, not a new lifecycle rule).

Example stale command response:

```json
{"timestamp":"2026-09-10T00:00:00Z","status":409,"code":"VERSION_CONFLICT","message":"The request has changed. Reload and try again.","path":"/api/v1/maintenance-requests/42/start","traceId":"server-generated-id","fieldErrors":[]}
```

Use the [permission/state matrix](permission-state-matrix-v1.md) for command authorization. Future paged queries use explicit DTOs, stable sorting and bounded size; query/filter fields and public history projections require slice contracts. No managed JPA entity may be serialized at the API boundary.
