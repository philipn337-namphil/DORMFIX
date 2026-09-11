# Local development

Prerequisites: Git, Java 21 JDK (JAVA_HOME/PATH), Docker Engine/Desktop running Linux containers with Compose v2+, Python 3 for repository checks. The Gradle 8.14.3 wrapper downloads its pinned distribution; first build needs network. No global Gradle install required. Repository-local .tools used during foundation validation is ignored and not required for other developers.

From repository root, copy .env.example to .env (`Copy-Item .env.example .env` in PowerShell; `cp .env.example .env` on Unix). Set POSTGRES_PASSWORD to a unique local value. No real credential is provided or committed. Missing/blank password deliberately fails Compose interpolation. Do not publish rendered `docker compose config` output because it contains env secrets; use --quiet.

Full local stack:

```sh
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 180
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health/liveness
curl --fail http://127.0.0.1:8080/actuator/health/readiness
docker compose logs --tail=100 app
```

The app waits for PostgreSQL health then runs Flyway and Hibernate validation. The initial migration only creates Flyway bookkeeping, no business tables. API /api/v1/me returns 401 because authentication is deferred. No test user/login/password is seeded. Both ports bind only 127.0.0.1. PostgreSQL data persists in a named volume.

Host JVM workflow: `docker compose up -d db`, export DB_URL=jdbc:postgresql://localhost:5432/dormfix, DB_USERNAME=dormfix_local and DB_PASSWORD matching .env, then from backend run `./gradlew bootRun` (PowerShell `.\gradlew.bat bootRun`). If POSTGRES_DB/USER were customized, use those values in host env too. Compose .env is not automatically loaded by Spring or Gradle. Never run host and Compose app on port 8080 simultaneously.

Validation from root then backend:

```sh
python scripts/check_repository.py
cd backend
./gradlew check bootJar
./gradlew integrationTest
```

PowerShell uses `.\gradlew.bat` instead. Integration tests supply their own throwaway PostgreSQL credentials via Testcontainers and do not need .env or the local DB. Docker image builds run check/bootJar; integration tests run separately with a daemon, never inside the Dockerfile.

Stop safely: `docker compose stop` or `docker compose down` preserves the named database volume. Never add -v for routine shutdown/recovery. Changing POSTGRES_PASSWORD after initial DB creation does not rotate the existing database password; use a deliberate local SQL password rotation or an explicitly authorized disposable reset. See [troubleshooting](../operations/troubleshooting.md).
