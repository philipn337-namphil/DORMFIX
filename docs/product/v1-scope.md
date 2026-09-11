# Frozen V1 scope

V1 includes multi-role users, dormitory/building/space/residence/facility/category data, maintenance reporting, command-based processing, visit planning, S3 attachment metadata, comments, work logs, durable history, database notifications and basic administrator summary queries. Frontend is secondary.

No role may arbitrarily set request status, including SUPER_ADMIN. After CLOSED, another failure requires a new request to preserve recurring-failure analytics. AI is not a V1 dependency. V2 may add Outbox/SQS, a notification worker, image classification, category/urgency suggestions, duplicate detection and analytics. Java/Spring remains the business backend; a Python inference capability is separately bounded if justified.

Engineering baseline: Java 21, Spring Boot, Gradle, Web, Security, Data JPA, PostgreSQL, Flyway, Bean Validation, JUnit 5, Mockito when useful, Testcontainers for PostgreSQL fidelity, Docker/Compose, GitHub Actions, Ubuntu EC2/Nginx, RDS/S3. No speculative Redis, microservices, Kubernetes/EKS, or full CQRS infrastructure. ECR/ECS are possible later evolution.

Phase 0 contains engineering harness, configuration, operational endpoints and representative tests only. No product API, entity workflow, authentication implementation, paid resources, production deployment or GitHub push is authorized by this phase.

See [ERD](erd-v1.md), [API](api-spec-v1.md), [permissions and state](permission-state-matrix-v1.md), [aggregate boundaries](../architecture/aggregate-boundaries.md), [transactions](../architecture/transaction-boundaries.md) and [events](../architecture/domain-events.md). These documents jointly capture the frozen baseline. Unspecified behavior stays unresolved in [review notes](../architecture/open-questions.md).
