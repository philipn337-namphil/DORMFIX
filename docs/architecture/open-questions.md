# Architecture review register - unresolved, baseline preserved

Status: OPEN unless explicitly approved by the human owner. These are gaps or tensions, not authorization to redesign V1. The current foundation prompt outranks historical context, whose conflicting details are noted below. No blocking business DDL/features are implemented in Phase 0.

| ID | Evidence / issue | Proposed review and affected slice |
|---|---|---|
| Q01 | /auth/refresh and /logout exist but ERD has no refresh-token store | ADR-013 proposes a technical token store; approve persistence, rotation/reuse, revocation and browser transport before authentication |
| Q02 | ADMIN is dormitory-scoped but no staff-to-dormitory relationship is present | ADR-014; do not infer scope from student Residence or grant all-dormitory admin access |
| Q03 | Residence disallows concurrent active records; exact date inclusivity, future dates and overlap semantics unspecified | Approve date interval and transactional/DB enforcement before Residence migrations; historical context's end_date IS NULL conflicts with future end dates |
| Q04 | Visits snapshot consent; update/actual_started_at rules and existing visits after reassignment/terminal request unclear | Review worker rights, cancellation/rescheduling and timestamps; never allow former worker commands or silently add visit start state/API |
| Q05 | active requests and exact update/communication state allow-lists are not fully defined | Approve resident/admin PATCH fields and states, WorkLog write/read states, comment deletion, historical worker read visibility and attachment permissions before those slices |
| Q06 | Duplicate target is original but valid target states, self/cycle/chain and cross-scope rules unspecified | Define checks without changing REPORTED -> DUPLICATE or adding transitions |
| Q07 | Some column nullability/defaults/FK actions, user_roles columns, request-number format, full history/notification enums absent | Approve each owning migration; ERD captures unspecified items literally; no arbitrary NOT NULL or enum completion |
| Q08 | Request assignment is active while unassigned_at NULL; assignment ending on resolve/close/reopen unspecified | Approve retention/current-assignment behavior; older context says reopen retains worker, but current prompt does not define all termination effects |
| Q09 | Multi-role users exist; combined resident/staff visibility/consent authority is not fully defined | Review deterministic policies; never use role union to silently override resident privacy restrictions |
| Q10 | AFTER_COMMIT notifications are allowed, outbox excluded | Separate new write transaction is engineering requirement; product loss/retry tolerance needs acceptance before notifications; no exactly-once guarantee |
| Q11 | S3 registration/deletion restrictions lack concrete size/type/expiry/cleanup policies | Approve attachment contract and compensating cleanup before feature/upload-maintenance-attachment |
| Q12 | Historical context permits admin request creation and mandates residence validation; current prompt only explicitly grants resident creation | Preserve current role baseline, clarify staff creation/eligibility instead of importing old extension |
| Q13 | History metadata JSONB may contain staff data; public projection and WorkLog visibility unspecified | Review redaction/query policy before history/communication endpoints; resident STAFF_ONLY prohibition remains absolute |
| Q14 | Auth signup, suspended-user access, privileged role administration and token invalidation details incomplete | Phase 1 security contract; no user-selected admin signup or stale-role bypass |

No contradiction was found in the explicit request transition table. The above are incomplete contracts and tensions with older context. Referenced context has additional earlier proposals (FastAPI/ECS, extra states) that are explicitly non-authoritative. Do not adopt them.

Resolution process: record evidence and concrete options in an ADR/review note -> human approval -> update affected product/architecture docs and harness with the approved change -> implement the owning vertical slice with tests. If approval changes frozen product design, call that out explicitly. Unaffected foundation work continues while these remain open.
