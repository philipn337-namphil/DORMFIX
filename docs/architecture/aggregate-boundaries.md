# Frozen aggregate boundaries

MaintenanceRequest exclusively controls its lifecycle through explicit assign(), start(), hold(), resume(), resolve(), close(), reopen(), reject(), markDuplicate() methods. No setter or API can bypass state invariants, even for SUPER_ADMIN. Preserve the exact [matrix](../product/permission-state-matrix-v1.md).

MaintenanceAssignment, MaintenanceVisit, Attachment, Comment, WorkLog, RequestHistory and Notification remain separate aggregates/repositories. User, Dormitory, Building, Space, Residence, Facility and MaintenanceCategory are also independent. Do not build a request entity with every related collection or broad bidirectional graph. Prefer scalar IDs for cross-aggregate links and explicit queries/projections.

Application services coordinate changes across these boundaries in one local transaction where the command requires atomicity. Domain objects enforce local transitions/invariants; application authorization includes relationships and state; database constraints reinforce race-sensitive invariants. Keeping aggregates small does not mean assignment/history can commit separately from the request command.

WorkLog/RequestHistory are append-only records, not mutable child collections. Visits snapshot entry consent and do not resolve requests. S3 metadata registration is its own authorized action. RequestHistory and events have separate purposes. No production domain objects are implemented in Phase 0.
