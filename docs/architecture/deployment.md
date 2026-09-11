# AWS / Ubuntu deployment architecture

초기 목표 경로는 Internet HTTPS → Ubuntu EC2의 Nginx → `127.0.0.1:8080` Dockerized Spring Boot다. RDS PostgreSQL과 private S3는 managed external service다. Foundation에서는 resource나 deployment를 만들지 않는다. SQS/notification worker/ECR/ECS는 이후 단계이고 EKS/Kubernetes는 사용하지 않는다.

실제 배포 시 지원되는 Ubuntu LTS, patched Docker Engine/Compose/Nginx, unattended security patch와 planned reboot를 사용한다. Nginx가 TLS를 종료하고 trusted forwarded-header source가 된다. EC2 instance role은 prefix/action이 제한된 S3와 secret retrieval만 수행한다. Public S3를 차단하고 TLS/encryption, RDS encryption/backups/PITR/verify-full TLS를 적용한다. DDL identity와 runtime DML identity를 분리하고 secret file은 `/etc/dormfix/app.env` mode 0600으로 checkout 밖에 둔다.

Local은 Flyway를 자동 실행하지만 production profile은 auto-migration을 끄고 Hibernate validation만 수행한다. Release gate가 matching migration으로 validate/migrate한 뒤 image를 전환한다. Rollback은 schema-compatible immutable image digest를 선택해 readiness/smoke를 재검증하며 SQL을 맹목적으로 되돌리거나 최신 write를 덮어쓰지 않는다. 상세 절차는 [Ubuntu runbook](../operations/ubuntu-deployment.md), [rollback](../operations/rollback.md), [CI/CD](../development/ci-cd.md)를 따른다.
