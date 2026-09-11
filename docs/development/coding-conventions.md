# Coding conventions

Use Java 21, four spaces, UTF-8, explicit names and constructor injection. Prefer records for immutable DTO/value payloads, ordinary classes for entities/use cases. Do not add Lombok or generic base services to save a few lines. Keep methods focused on business intent, and comments on why an invariant exists.

Use package-by-feature and explicit command services; no giant service, static service locator, controller transaction or entity HTTP response. Mapping happens at API/application boundary. Domain methods are named business actions and validate their own state. Application authorization checks role/relationship/state inside the transaction. See [backend architecture](../architecture/backend-architecture.md).

Bean Validation covers shape/range; domain/application handles state/relationship. Use stable error codes with safe messages and reviewed mappings. First feature needing general exception translation adds a focused @RestControllerAdvice and tests for 400/404/409/500; the foundation implements only the security error path and shared error record. Never return fake success for deferred endpoints.

Tests describe behavior, use Clock for time-sensitive logic and Mockito only for useful boundaries. Do not mock JPA to prove PostgreSQL constraints. Avoid raw generic query return types in production. Prefer composition and minimal interfaces at real technical boundaries. Keep logs free of sensitive data.

Checkstyle currently enforces no tabs, no star/unused imports and naming/filename consistency. Expand policy with demonstrated value; do not impose style churn on unrelated features. Boot BOM manages framework dependency versions; explicit extra-tool versions are pinned in Gradle. Review dependency patch/support/security status before deployment.
