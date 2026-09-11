# API v1.0 - frozen contract

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

Admin group /admin/... has representative POST/PATCH/deactivate facilities, POST/PATCH/deactivate categories, POST/PATCH residences, GET users and GET dashboard summary. Exact admin paths and DTOs are not fully specified. Dormitory/Building/Space and role/master-data administration belong to SUPER_ADMIN. Do not invent a generic delete/status endpoint.

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
| 409 | Business state/concurrency conflict | INVALID_REQUEST_STATE, REQUEST_ALREADY_ASSIGNED, VERSION_CONFLICT |

INVALID_WORKER is also a representative frozen code; exact validation-vs-business-conflict mapping must be specified by assignment API review. Do not map every integrity constraint error to VERSION_CONFLICT.

Standard body fields: timestamp, status, code, message, path, traceId, fieldErrors. Foundation uses an ISO-8601 Instant timestamp, numeric status, stable string code, safe message, request path without query, server-generated traceId and fieldErrors list of {field, message}. Validation errors must not echo rejected secret values. Unexpected errors use safe 500 INTERNAL_ERROR (engineering fallback, not a new lifecycle rule).

Example stale command response:

```json
{"timestamp":"2026-09-10T00:00:00Z","status":409,"code":"VERSION_CONFLICT","message":"The request has changed. Reload and try again.","path":"/api/v1/maintenance-requests/42/start","traceId":"server-generated-id","fieldErrors":[]}
```

Use the [permission/state matrix](permission-state-matrix-v1.md) for command authorization. Future paged queries use explicit DTOs, stable sorting and bounded size; query/filter fields and public history projections require slice contracts. No managed JPA entity may be serialized at the API boundary.
