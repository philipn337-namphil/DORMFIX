# Local and operational troubleshooting

| Symptom | Check and safe action |
|---|---|
| Java/wrapper fails | Verify JAVA_HOME and java -version show Java 21; use checked-in wrapper, first download needs network |
| Gradle plugin/dependency resolution fails | Check network/proxy access and pinned version; do not silently downgrade or disable tests |
| Docker daemon missing | Start Docker Desktop/Engine with Linux containers; check docker info; do not skip Testcontainers |
| Windows Testcontainers cannot find daemon | Check active Docker context/pipe and Docker Desktop readiness; avoid exposing an unauthenticated TCP daemon |
| Compose interpolation fails | Copy .env.example and set nonempty POSTGRES_PASSWORD; use config --quiet |
| DB authentication fails after env edit | Existing volume retains old role password; changing POSTGRES_PASSWORD does not rotate it |
| Port 5432/8080 occupied | Stop the conflicting local process/stack intentionally or use a reviewed local port override |
| App startup migration fails | Inspect Flyway history/checksum, DB connectivity and pending migration; no clean/repair as shortcut |
| Readiness 503 | DB dependency or application availability is down; liveness may remain 200 by design |
| /api/v1/me returns 401 | Expected foundation behavior: authentication is not implemented |
| Actuator env returns 401/403 | Expected: sensitive endpoints are not exposed |
| Notification missing after commit | V1 events are not durable; inspect handler logs and separate transaction; request may already have committed |
| Container unhealthy but running | Docker health status alone does not trigger restart; inspect app/DB and respond to alert |
| Production TLS/DB errors | Check cert expiry/hostname/RDS CA/verify-full, security groups, IAM and secrets without logging secret values |

Collect exact command, safe error code, request trace ID, image/schema version and sanitized logs. Do not paste .env, rendered Compose secrets, signed URLs or bearer tokens into issues. Preserve database volumes/history during investigation.
