# Vertical implementation roadmap

Phase 0 is foundation only. Do not start Phase 1 until the human reviews this baseline. Every phase uses the [Definition of Done](definition-of-done.md); a feature includes API, application, domain, persistence/migration, tests and docs. No technical-layer branches. Review unresolved contracts before their dependent code, preserving frozen semantics.

| Phase | Vertical branch / outcome | Dependency and acceptance evidence |
|---|---|---|
| 0 | chore/foundation | Harness, docs, skeleton, CI/local templates; no product behavior |
| 1 | feature/authentication | Resolve Q01/Q14 and ADR-013; User/roles, hashing, login/access/refresh/logout/me with negative auth tests; only approved technical storage changes |
| 2 | feature/location-catalog-baseline | Dormitory/Building/Space/Facility/Category queries and required authorized master-data setup; resolve Q02/Q07 and ADR-014; query scope/validation tests |
| 3 | feature/residence-management | Residence history depends on User/ROOM Space; resolve Q03, migration and concurrent overlap/ROOM validation tests |
| 4 | feature/create-maintenance-request | Resident eligibility, location/category/facility, consent/preference, REPORTED request and atomic REQUEST_CREATED history; resolve numbering/nullability; no fake notifications |
| 5 | feature/query-update-maintenance-request | Scoped list/detail/current-worker query extension and REPORTED resident update, admin allowed metadata; exact DTO/privacy/version conflicts approved and tested |
| 6 | feature/upload-maintenance-attachment | S3 presign/register/download, key ownership, limits/content checks and cleanup policy; external I/O outside DB transaction |
| 7 | feature/assign-worker | Initial/reassignment with request @Version, one-active partial index, history and current/former worker tests; Q08 assignment effects reviewed |
| 8 | feature/triage-maintenance-request | Reject and mark duplicate REPORTED commands; Q06 target/scope rules approved; terminal and concurrent behavior tests |
| 9 | feature/start-maintenance | start/hold/resume as small coherent worker slices; current assignment and invalid-state/version tests |
| 10 | feature/manage-visit | Schedule/reschedule/complete/no-access/cancel; Q04 approved; immutable consent snapshots; no request resolution side effect |
| 11 | feature/maintenance-communication | WorkLog append-only and PUBLIC/STAFF_ONLY comments/soft-delete; Q05/Q13 visibility and state contract; former worker denied |
| 12 | feature/resolve-maintenance | Current-worker IN_PROGRESS -> RESOLVED, timestamp/history/version atomicity |
| 13 | feature/confirm-maintenance | Reporter/admin close/reopen RESOLVED; CLOSED remains terminal and recurrence creates new request |
| 14 | feature/history-notification | Public history projection and DB notification delivery/read commands; audit writes already exist from each command; AFTER_COMMIT/new transaction and accepted loss budget |
| 15 | feature/admin-dashboard | Scoped summary queries and remaining authorized facility/category/residence management, query/index evidence |
| 16 | chore/container-hardening | Image digest/version/security review, limits, staging smoke, reboot/runtime/log checks |
| 17 | chore/ubuntu-staging | Explicitly authorized Ubuntu/Nginx/TLS/IAM environment; validate templates and operations learning |
| 18 | feature/production-storage | Authorized private RDS/S3 integration, TLS/secrets, migration identity, backups/PITR and restore rehearsal |
| 19 | chore/continuous-delivery | Registry and protected GitHub Actions deployment, migration gate, health checks and compatible rollback |
| 20 | chore/production-hardening | CloudWatch alerts, load/security tests, retention policies, incident/restore/reboot drills before launch |
| V2 | feature/async-notification and separately scoped AI/analytics slices | Approved outbox + SQS + idempotent worker/retry/DLQ first if needed; AI classification/suggestions/duplicate detection/recurrence analytics optional and separately bounded |

Ordering differs from a simple feature list for concrete dependencies: Residence needs User and ROOM Space first; durable RequestHistory is built with each changing command, not postponed until its read endpoint; reject/duplicate triage is explicitly included; deployment depends on storage/security and restore validation. No product transition/authorization semantics are changed by this ordering.

Each row may split into smaller use-case branches if review size grows, while each resulting branch still delivers a complete vertical behavior. Do not merge incomplete fake endpoints or use TODOs to conceal required authorization. Frontend remains minimal and consumes tested backend contracts.
