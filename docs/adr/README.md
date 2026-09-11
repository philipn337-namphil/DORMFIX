# ADR index

ADR은 architecture decision의 배경, 결정, 대안, 장단점, 결과를 기록한다. Product semantics는 `docs/product/`의 Frozen authority이고 ADR은 이를 변경하지 않는다. 변경이 필요하면 evidence → proposed ADR → alternatives/trade-offs → human approval → docs/harness/tests 동기화 → implementation 순서를 따른다.

현재 ADR-001~014는 Java/Spring, PostgreSQL, modular monolith, package-by-feature, command API, optimistic locking, Flyway/JPA, S3 presigned upload, V1 Spring event와 V2 Outbox/SQS, Ubuntu EC2, Docker, vertical Git workflow, refresh token 제안, administrator scope를 다룬다.
