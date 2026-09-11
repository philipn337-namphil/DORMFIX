# Ubuntu deployment runbook (future execution only)

Foundation에서는 실제 server/resource를 만들지 않았다. 명시적 deployment authorization과 architecture/security acceptance 후에만 수행한다. 이 문서는 deploy script가 아닌 checklist다.

SSH 후 `/opt/dormfix`에 검토된 release를 두고 `/etc/dormfix/app.env` mode 0600과 RDS CA를 준비한다. DB는 verify-full TLS를 사용하고 runtime user는 필요한 DML만 가진다. DDL identity는 분리한다. Flyway validate/migrate는 release migration과 전용 role로 한 번 수행하고 결과 schema version을 기록한다. Production app profile은 automatic migration을 끈다.

Production Compose를 실제 env file과 함께 `config --quiet`로 검증하고 systemd Docker service를 enable한다. Nginx TLS/forwarding, liveness/readiness, HTTPS smoke, log rotation, restart/reboot recovery를 확인한다. DNS, region, ARN, credential, cloud resource는 이 template에 포함하지 않는다.
