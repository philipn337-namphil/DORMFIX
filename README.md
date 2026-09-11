# DormFix

Dormitory maintenance workflow: REPORT -> ASSIGN -> VISIT -> REPAIR -> CONFIRM.

This repository contains the **engineering foundation only**: Java 21/Spring Boot skeleton, PostgreSQL/Flyway wiring, safe health/security/logging boundaries, architecture checks, CI, Docker and deployment templates. Business APIs, authentication and maintenance entities are intentionally not implemented. Frozen V1 contracts live in [docs](docs/README.md).

## Start locally

Install Java 21 (for host builds), Docker with Linux containers/Compose, and Python 3 (repository checks). No global Gradle install is needed.

1. Copy `.env.example` to `.env` and set a unique local `POSTGRES_PASSWORD`.
2. From this directory run:

```sh
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 180
curl --fail http://127.0.0.1:8080/actuator/health/liveness
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

Local PostgreSQL uses a persistent volume and loopback port 5432. The app uses loopback 8080; Flyway runs at startup. `/api/v1/me` returns 401 until authentication is implemented. Stop with `docker compose down` (preserves data). Never use `down -v` for routine shutdown. See [full setup and host-JVM workflow](docs/development/local-development.md).

## Validate

```sh
python scripts/check_repository.py
cd backend
./gradlew check bootJar
./gradlew integrationTest
```

In PowerShell use `.\gradlew.bat` instead of `./gradlew`. Docker must be running for integration tests; they fail rather than skip without it. First build downloads dependencies. [Validation record](docs/development/foundation-validation.md) separates executed checks from environment blockers.

## Repository map

| Path | Responsibility |
|---|---|
| AGENTS.md | Root engineering harness and required gates |
| backend/ | Spring Boot application and architecture/API/PostgreSQL tests |
| frontend/ | Deferred minimal client boundary |
| infra/ | Production Compose, Nginx and systemd templates |
| docs/product/ | Frozen ERD/API/permissions/state rules |
| docs/architecture/, docs/adr/ | Architecture decisions and unresolved proposals |
| docs/development/, docs/operations/ | Workflow, roadmap and runbooks |
| scripts/ | Repository contract checks |
| .github/ | CI, dependency updates and PR checklist |

Read [AGENTS.md](AGENTS.md) before changing code. Use vertical feature branches, never technical-layer branches or direct main feature work. The initial local branch is `chore/foundation`; no remote push/deployment is performed. [Review questions](docs/architecture/open-questions.md), [ADRs](docs/adr/README.md) and [roadmap](docs/development/roadmap.md) define what needs approval before the next slice.

Production templates are plans, not a deployed system. The intended path is HTTPS -> Ubuntu EC2/Nginx -> Docker Spring Boot, with private RDS/S3. See [deployment architecture](docs/architecture/deployment.md). No real secrets, AWS resources or AI/V2 infrastructure are included.
