# Backend scope

Read root AGENTS and `docs/architecture/backend-architecture.md`, `database.md`, `security.md`, and `docs/development/testing-strategy.md` for the relevant change.

Use Java 21, checked-in Gradle wrapper, Spring Boot BOM-managed dependencies. `check` includes unit/API/architecture tests and Checkstyle. `integrationTest` is a separate required PostgreSQL/Testcontainers gate; never add skip-on-no-Docker. No H2 substitute for PostgreSQL tests.

Place business code under the owning feature's api/application/domain/infrastructure package. `platform` holds small technical cross-cutting facilities only; it must not depend on feature code. Do not add placeholder REST endpoints that claim a feature works. The foundation denies all business access until authentication is implemented.

Keep entity constructors protected for JPA and public creation/transition methods explicit. Avoid Lombok @Data on entities. Application services own transactions and authorization; controllers own mapping/validation only. Test the negative path as well as success. No production entities or business migrations are currently implemented.
