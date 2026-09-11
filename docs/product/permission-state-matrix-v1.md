# Frozen V1 permissions and state machine

Authorization evaluates authentication -> role -> resource relationship/scope -> current state. An authenticated wrong role, non-owner or former assignee receives 403, not 409. State and version conflicts for an authorized actor receive 409. SUPER_ADMIN includes ADMIN operations but never bypasses lifecycle rules. Multi-role combination details and dormitory-scope persistence require review.

| Command | Source | Target | Authorized relationship |
|---|---|---|---|
| assign | REPORTED | ASSIGNED | ADMIN/SUPER_ADMIN within dormitory scope |
| reassign | ASSIGNED, IN_PROGRESS, ON_HOLD | ASSIGNED | ADMIN/SUPER_ADMIN within scope |
| start | ASSIGNED | IN_PROGRESS | current active worker |
| hold | IN_PROGRESS | ON_HOLD | current active worker |
| resume | ON_HOLD | IN_PROGRESS | current active worker |
| resolve | IN_PROGRESS | RESOLVED | current active worker |
| close | RESOLVED | CLOSED | reporter RESIDENT or scoped ADMIN/SUPER_ADMIN |
| reopen | RESOLVED | IN_PROGRESS | reporter RESIDENT or scoped ADMIN/SUPER_ADMIN |
| reject | REPORTED | REJECTED | scoped ADMIN/SUPER_ADMIN |
| mark duplicate | REPORTED | DUPLICATE | scoped ADMIN/SUPER_ADMIN; reference original |

CLOSED, REJECTED and DUPLICATE are terminal. No other transition is authorized. A CLOSED request cannot reopen. Reassignment closes the previous assignment and creates a new one; the new worker explicitly starts work again. At most one assignment per request has unassigned_at IS NULL, reinforced by a PostgreSQL partial unique index.

## Resident

Create requests; read own requests; edit own request only in REPORTED; add PUBLIC comments and allowed attachments to own active requests; view own visit schedules; close/reopen own RESOLVED request. Cannot directly set status, priority, reporter or assignment. STAFF_ONLY comments are never visible. Active-state definition for communication and exact attachment/deletion permissions remain review questions, not invented rules.

## Worker

Only the current active assignee may start/hold/resume/resolve, create/manage visits in valid states, write WorkLogs, write PUBLIC/STAFF_ONLY comments and add repair attachments. A previously assigned worker may retain read-only historical access, but may not execute commands, comment or add WorkLogs. Historical read projections must not accidentally become write authority.

## Administrator

ADMIN can read all requests within dormitory scope, adjust category/priority/location for active requests according to API rules, assign/reassign, reject or mark REPORTED requests duplicate, schedule visits, close/reopen RESOLVED requests and manage facilities/categories/residences. Cannot modify resident-controlled room-entry consent on the resident's behalf.

SUPER_ADMIN adds Dormitory/Building/Space, user-role and system/master-data management. All domain state rules still apply.

## Visit and entry consent

Visit statuses: SCHEDULED, COMPLETED, CANCELED, NO_ACCESS. Creation is allowed only when the request is ASSIGNED, IN_PROGRESS or ON_HOLD. Visit completion does not resolve the request. Snapshot entry_policy into entry_policy_snapshot and contact_before_entry into the visit at creation; these snapshots remain immutable despite later request changes.

EntryPolicy: RESIDENT_PRESENT_REQUIRED or ABSENT_ENTRY_ALLOWED. contact_before_entry is independent: absent entry may be allowed while prior contact remains required. No admin consent override. Visit modification/terminal transition details, actual start timestamp ownership and scheduled visits during reassignment need explicit review before implementation.

## Retention

Do not casually hard-delete User, Dormitory, Building, Space, Facility, MaintenanceCategory, MaintenanceRequest, MaintenanceAssignment, MaintenanceVisit, WorkLog or RequestHistory. Use status/active changes, e.g. Facility RETIRED, category inactive. Preserve historical Residence records. Comment uses soft deletion. WorkLog and RequestHistory are append-only in normal operation. Attachment deletion is restricted and must coordinate S3 cleanup. Notification retention may be defined later.
