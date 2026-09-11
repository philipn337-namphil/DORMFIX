# Observability foundation

Application log는 Spring Boot structured console(Logstash JSON)를 사용한다. `RequestTraceFilter`는 request마다 UUID를 만들고 `X-Request-ID`, safe error body의 `traceId`, MDC를 설정하며 method/status/durationMs를 기록하고 finally에서 MDC를 정리한다. 외부에서 들어온 ID를 무조건 신뢰하지 않으며 query string, body, token header, private comment, signed URL을 log하지 않는다.

이는 request correlation이며 distributed tracing 전체 구현이 아니다. Async handler가 추가되면 correlation propagation을 별도로 설계한다. 첫 API slice에서 tested exception handler/observation convention을 추가한다.

Ubuntu에서는 Docker json-file rotation을 제한하고 검토된 CloudWatch Agent/log driver로 stdout을 수집한다. 환경별 log group, 제한된 IAM, retention, error/readiness/restart/disk/CPU/memory/RDS/migration alarm을 운영 checklist로 관리한다. V1에는 full metrics/tracing stack이나 public actuator dump endpoint를 추가하지 않는다. 외부에는 최소 health probe만 노출한다.
