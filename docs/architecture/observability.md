# Observability foundation

Endpoints: GET /actuator/health/liveness (process availability, no DB dependency) and GET /actuator/health/readiness (application readiness plus PostgreSQL). Both expose status only, no components/details. All other Actuator URLs are denied and only health is exposed. These are the chosen equivalents of /health and /ready; do not create contradictory aliases. Nginx limits probes to local/operator traffic in production.

Application logs use Spring Boot structured console logging (Logstash JSON). RequestTraceFilter generates a new UUID per request, returns X-Request-ID, attaches traceId to safe error bodies and MDC, logs method/status/durationMs, and cleans MDC in finally. Untrusted inbound IDs are deliberately not adopted. No raw query strings, bodies, token headers, private comments or signed URLs are logged. This is request correlation, not distributed tracing. Async handlers will need explicit correlation propagation when introduced. Unhandled servlet failures may be logged before container error dispatch finalizes status; add a tested exception handler/observation convention with the first API slice.

CI/API tests check trace propagation and error shape. Full PostgreSQL tests check readiness/liveness visibility. Business errors should log stable codes and IDs at sensible severity; unexpected failures are 500 with safe public messages and internal stack traces scrubbed of sensitive values. No per-read domain events.

On Ubuntu, keep Docker json-file rotation bounded; collect application stdout through a reviewed CloudWatch Agent/log driver configuration. Use separate log groups per environment, restricted IAM, retention and alarms for error rate, readiness failure, restart loops, disk, CPU/memory, RDS connections/storage and migration failures. Log shipping config/retention thresholds are deployment prerequisites, not provisioned in Phase 0. No full metrics/tracing stack or public actuator dump endpoints in V1.

Spring Boot's [Actuator reference](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html) describes health groups and exposure. The foundation chooses the minimal two-probe access surface.
