# ERD v1.0 - frozen logical schema

This is the authoritative column inventory from the 2026-09-10 specification: 15 entities plus user_roles. SQL storage names are an engineering mapping (snake_case; User maps to app_user). This document does not invent missing nullability/defaults, notification types or history enum completeness. No business DDL is shipped in Phase 0; each slice adds reviewed Flyway migrations. ENUM denotes the logical enum; string columns with CHECK constraints are the proposed physical convention in ADR-007.

## User

```text
id BIGINT PK
email VARCHAR(255) UNIQUE NOT NULL
password_hash VARCHAR(255) NOT NULL
name VARCHAR(50) NOT NULL
phone VARCHAR(20) NULL
student_number VARCHAR(30) NULL UNIQUE
status ENUM NOT NULL
last_login_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ NOT NULL
updated_at TIMESTAMPTZ NOT NULL
```

## Dormitory

```text
id BIGINT PK
name VARCHAR(100)
address VARCHAR(255)
timezone VARCHAR(50)
active BOOLEAN
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

## Building

```text
id BIGINT PK
dormitory_id BIGINT FK -> Dormitory
code VARCHAR(30)
name VARCHAR(100)
active BOOLEAN
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

## Space

```text
id BIGINT PK
building_id BIGINT FK -> Building
code VARCHAR(50)
name VARCHAR(100)
type ENUM
floor INTEGER
description VARCHAR(500) NULL
active BOOLEAN
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

## Residence

```text
id BIGINT PK
resident_id BIGINT FK -> User
room_space_id BIGINT FK -> Space
start_date DATE
end_date DATE NULL
created_at TIMESTAMPTZ
```

## Facility

```text
id BIGINT PK
space_id BIGINT FK -> Space
name VARCHAR(100)
facility_type VARCHAR(50)
asset_code VARCHAR(100) NULL UNIQUE when present
status ENUM
installed_at DATE NULL
description VARCHAR(500) NULL
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

## MaintenanceCategory

```text
id BIGINT PK
parent_id BIGINT FK -> MaintenanceCategory NULL
code VARCHAR(50) UNIQUE
name VARCHAR(100)
default_priority ENUM
active BOOLEAN
sort_order INTEGER
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

## MaintenanceRequest

```text
id BIGINT PK
request_number VARCHAR(30) UNIQUE
reporter_id BIGINT FK -> User
space_id BIGINT FK -> Space
facility_id BIGINT FK -> Facility NULL
category_id BIGINT FK -> MaintenanceCategory
title VARCHAR(150)
description TEXT
priority ENUM
status ENUM
entry_policy ENUM
contact_before_entry BOOLEAN
preferred_visit_start TIMESTAMPTZ NULL
preferred_visit_end TIMESTAMPTZ NULL
duplicate_of_id BIGINT FK -> MaintenanceRequest NULL
status_reason VARCHAR(500) NULL
resolved_at TIMESTAMPTZ NULL
closed_at TIMESTAMPTZ NULL
version BIGINT
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

## MaintenanceAssignment

```text
id BIGINT PK
request_id BIGINT FK -> MaintenanceRequest
worker_id BIGINT FK -> User
assigned_by_id BIGINT FK -> User
assigned_at TIMESTAMPTZ
unassigned_at TIMESTAMPTZ NULL
unassigned_by_id BIGINT FK -> User NULL
reason VARCHAR(500) NULL
```

## MaintenanceVisit

```text
id BIGINT PK
request_id BIGINT FK -> MaintenanceRequest
worker_id BIGINT FK -> User
created_by_id BIGINT FK -> User
scheduled_start TIMESTAMPTZ
scheduled_end TIMESTAMPTZ
actual_started_at TIMESTAMPTZ NULL
actual_ended_at TIMESTAMPTZ NULL
status ENUM
entry_policy_snapshot ENUM
contact_before_entry BOOLEAN
note VARCHAR(500) NULL
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
```

## Attachment

```text
id BIGINT PK
request_id BIGINT FK -> MaintenanceRequest
uploader_id BIGINT FK -> User
type ENUM
object_key VARCHAR(500) UNIQUE
original_filename VARCHAR(255)
content_type VARCHAR(100)
file_size BIGINT
created_at TIMESTAMPTZ
```

## Comment

```text
id BIGINT PK
request_id BIGINT FK -> MaintenanceRequest
author_id BIGINT FK -> User
content TEXT
visibility ENUM
created_at TIMESTAMPTZ
deleted_at TIMESTAMPTZ NULL
```

## WorkLog

```text
id BIGINT PK
request_id BIGINT FK -> MaintenanceRequest
visit_id BIGINT FK -> MaintenanceVisit NULL
worker_id BIGINT FK -> User
action_type ENUM
description TEXT
created_at TIMESTAMPTZ
```

## RequestHistory

```text
id BIGINT PK
request_id BIGINT FK -> MaintenanceRequest
actor_id BIGINT FK -> User NULL
event_type ENUM
previous_status ENUM NULL
new_status ENUM NULL
metadata JSONB NULL
created_at TIMESTAMPTZ
```

## Notification

```text
id BIGINT PK
recipient_id BIGINT FK -> User
request_id BIGINT FK -> MaintenanceRequest NULL
type ENUM
title VARCHAR(150)
message VARCHAR(500)
read_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ
```

## Supporting user_roles

A user may hold multiple roles: RESIDENT, WORKER, ADMIN, SUPER_ADMIN. The supporting table/collection is named user_roles; the frozen specification does not give its exact columns. Proposed physical mapping is user_id FK plus role with a composite primary key; approve it with the authentication migration.

## Enum inventory

| Enum / field | Frozen values |
|---|---|
| UserStatus | ACTIVE, SUSPENDED, WITHDRAWN |
| Role | RESIDENT, WORKER, ADMIN, SUPER_ADMIN |
| SpaceType | ROOM, CORRIDOR, LOUNGE, LAUNDRY_ROOM, RESTROOM, LOBBY, KITCHEN, STUDY_ROOM, OTHER |
| FacilityStatus | ACTIVE, OUT_OF_SERVICE, RETIRED |
| Priority | LOW, NORMAL, HIGH, EMERGENCY |
| Request status | REPORTED, ASSIGNED, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED, REJECTED, DUPLICATE |
| Entry policy and visit snapshot | RESIDENT_PRESENT_REQUIRED, ABSENT_ENTRY_ALLOWED |
| Visit status | SCHEDULED, COMPLETED, CANCELED, NO_ACCESS |
| AttachmentType | REPORT, BEFORE_REPAIR, AFTER_REPAIR, OTHER |
| CommentVisibility | PUBLIC, STAFF_ONLY |
| WorkActionType | INSPECTION, REPAIR, REPLACEMENT, TEST, NOTE |
| History previous/new status | Request status, nullable |
| RequestHistory event_type | Examples below; complete set not specified |
| Notification type | Not specified; review before DDL |

RequestHistory examples: REQUEST_CREATED, STATUS_CHANGED, PRIORITY_CHANGED, CATEGORY_CHANGED, ASSIGNED, UNASSIGNED, VISIT_SCHEDULED, VISIT_RESCHEDULED, VISIT_COMPLETED, MARKED_DUPLICATE, REOPENED, CLOSED. actor_id may be null for system actions. These examples are not a complete enum declaration.

## Frozen constraints and indexes

- Building UNIQUE(dormitory_id, code); Space UNIQUE(building_id, code).
- User email and student_number unique; nullable unique values use PostgreSQL normal null semantics. Category code, request_number and attachment object_key unique. Facility asset_code unique when present.
- Residence room_space must be ROOM; no resident has more than one concurrently active Residence; preserve historical records. Date boundary/overlap definition remains open.
- Categories may be hierarchical. Do not replace them with a hard-coded category enum.
- MaintenanceRequest is the central aggregate with JPA @Version optimistic locking. Index access patterns: (status, created_at), (space_id, status), (reporter_id, created_at), (category_id, created_at), (priority, status).
- MaintenanceAssignment has at most one active assignment per request, defined as unassigned_at IS NULL. Planned physical constraint: CREATE UNIQUE INDEX ux_assignment_active_request ON maintenance_assignment(request_id) WHERE unassigned_at IS NULL.
- Duplicate requests reference the original request. No invented chain/cycle policy.
- Visit consent snapshot is immutable after creation. Visits are distinct from request resolution.
- Actual attachment binaries live in S3; DB holds metadata and object keys only.
- WorkLog and RequestHistory are append-only in normal operation. Comment soft deletion uses deleted_at. See the frozen [retention policy](permission-state-matrix-v1.md).

No extra business entities, lifecycle states or role associations are introduced by this foundation. Foreign-key delete actions, unspecified nullability, user_roles mapping and additional checks must be resolved with the owning slice and tracked in [review notes](../architecture/open-questions.md).
