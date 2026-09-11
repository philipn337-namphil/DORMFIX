# Frozen V1 scope

V1은 multi-role user, dormitory/building/space/residence/facility/category data, maintenance reporting, command-based processing, visit planning, S3 attachment metadata, comments, work logs, durable history, database notification, administrator summary query를 포함한다. Frontend는 secondary scope다.

어떤 role도 SUPER_ADMIN을 포함해 request status를 임의로 지정할 수 없다. CLOSED 뒤의 재발은 recurring-failure analytics를 위해 새 request로 만든다. AI는 V1 dependency가 아니다. V2에서는 Outbox/SQS, notification worker, image classification, category/urgency suggestion, duplicate detection, analytics를 검토할 수 있다. Java/Spring은 business backend로 유지하고 Python inference는 필요할 때 별도 bounded capability로 둔다.

Engineering baseline은 Java 21, Spring Boot, Gradle, Web, Security, Data JPA, PostgreSQL, Flyway, Bean Validation, JUnit 5, Mockito, PostgreSQL fidelity를 위한 Testcontainers, Docker/Compose, GitHub Actions, Ubuntu EC2/Nginx, RDS/S3다. Redis, microservice, Kubernetes/EKS, full CQRS 같은 speculative 요소는 추가하지 않는다. ECR/ECS는 이후 선택지다.

Phase 0은 harness, configuration, operational endpoint, representative test만 제공한다. Product API, authentication 구현, maintenance entity workflow, paid resource, production deployment, GitHub push는 이 phase의 범위가 아니다.

상세 frozen baseline은 [ERD](erd-v1.md), [API](api-spec-v1.md), [권한과 state](permission-state-matrix-v1.md), [aggregate](../architecture/aggregate-boundaries.md), [transaction](../architecture/transaction-boundaries.md), [event](../architecture/domain-events.md)에 있다. 미정 동작은 [review note](../architecture/open-questions.md)에 남긴다.
