# Backend scope

Java 21, checked-in Gradle wrapper, Spring Boot BOM-managed dependency를 사용한다. `check`는 unit/API/architecture test와 Checkstyle을 포함하고 `integrationTest`는 PostgreSQL/Testcontainers gate다. Docker가 없을 때 skip하지 않으며 PostgreSQL fidelity를 위해 H2를 대체 사용하지 않는다.

Business code는 owning feature의 api/application/domain/infrastructure 아래 둔다. `platform`은 작은 technical cross-cutting facility만 담고 feature에 의존하지 않는다. 아직 동작하지 않는 feature를 가장하는 REST endpoint를 추가하지 않는다. Authentication 전까지 foundation은 business access를 deny한다.
