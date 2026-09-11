# Backend architecture

Root package com.dormfix. One executable Spring Boot application. Packages own features:

| Package | Responsibility |
|---|---|
| identity | User, multi-role authority, authentication |
| location | Dormitory, Building, Space, Residence history |
| catalog | Facility, MaintenanceCategory |
| maintenance | Request lifecycle and independent assignment/visit/communication/history aggregates |
| notification | Recipient-scoped notifications and event reaction |
| platform | Small web/security/logging/configuration facilities; no business dependencies |

Inside each feature, api maps HTTP/DTOs and Bean Validation, application owns use cases/queries/authorization/transactions and required ports, domain owns invariants and value data, infrastructure implements JPA/S3/technical adapters. package-info.java is an extension map; there are no pretend services or repositories.

Allowed direction: api -> application -> domain; infrastructure -> application/domain. Composition in platform/config may wire technical API components. API cannot reference domain entities or infrastructure. Application may depend on Spring transaction/event APIs, but not JpaRepository/EntityManager or AWS clients; introduce a narrow repository/storage port when a real use case needs one. Domain may use Jakarta Persistence mapping annotations; it cannot depend on Spring/web/AWS. This is the intentional JPA exception, not a requirement for duplicate persistence/domain models.

Across features, use an application contract or ID/value event; no access to another feature's infrastructure or private entity graph. Keep package dependencies acyclic. Avoid creating contracts until a real caller exists. platform never imports a feature. ArchUnit enforces the core directions and package cycles; review also checks method behavior and cross-feature API exposure that static dependency rules cannot prove.

Use explicit services: CreateMaintenanceRequestService, UpdateMaintenanceRequestService, AssignMaintenanceRequestService, StartMaintenanceService, HoldMaintenanceService, ResumeMaintenanceService, ResolveMaintenanceService, CloseMaintenanceService, ReopenMaintenanceService, RejectMaintenanceRequestService, MarkDuplicateRequestService. Queries use separate projection-oriented services. This is conceptual command/query separation, not full CQRS.

Controller -> use case with authenticated actor ID and validated DTO -> authorize/load/version check -> aggregate method -> persistence/history -> commit -> effects. Controllers never mutate managed entities or own transactions. No giant MaintenanceRequestService, generic CRUD status endpoint, global controller/service/repository buckets or speculative base abstractions.

The foundation security chain denies all non-probe traffic. Authentication is deliberately not implemented. The first authentication slice replaces that boundary with real Bearer validation and positive/negative tests; never add permitAll to make feature tests pass.
