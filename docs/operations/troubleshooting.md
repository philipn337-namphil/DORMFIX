# Local과 운영 troubleshooting

문제 발생 시 정확한 command, safe error code, request trace ID, image/schema version, sanitized log를 수집한다. `.env`, rendered Compose secret, signed URL, bearer token을 issue에 붙이지 않는다. 조사 중 DB volume과 history를 보존한다.

- Compose가 시작되지 않으면 `.env` 존재와 `POSTGRES_PASSWORD` interpolation을 확인한다.
- readiness가 실패하면 PostgreSQL health, DB URL/TLS, Flyway history와 safe application log를 확인한다.
- 401/403은 authentication, role, ownership/current assignment/scope를 구분한다.
- Migration checksum 오류는 applied migration을 수정하지 말고 reviewed repair/forward decision을 따른다.
- Production TLS/DB 오류는 certificate, hostname, RDS CA, verify-full, security group, IAM, secret을 secret 값 없이 점검한다.
