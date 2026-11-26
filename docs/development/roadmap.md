# Vertical implementation roadmap

각 phase는 [Definition of Done](definition-of-done.md)을 적용하며 API → Application → Domain → Persistence → Migration → Test → 필요한 Documentation을 포함하는 vertical slice로 개발한다. Layer 기반 branch는 금지한다. 이 문서는 계획이며 실제 cloud provisioning이나 배포를 승인하지 않는다. 배포 phase는 각 시점에 사용자의 명시적 승인을 받아 수행한다.

## 진행 현황 (2026-11-27)

| Phase | 내용 | 상태 |
|---|---|---|
| 0 | Foundation | 완료 (`chore/foundation`) |
| 1 | Authentication | 완료 (`feature/authentication`) |
| 2 | 구조 데이터 | 완료 (`feature/manage-dormitory-structure`, residence 포함) |
| 3 | 신고 핵심 | 완료 (PR #14 이후, ADR-019~027) |
| 4 | 배정 / 상태전이 | 완료 (Phase 3 workflow 슬라이스에 포함, PR #18) |
| 5 | Workflow 완성 | 일부 완료. Visit, WorkLog, metadata PATCH, Comment, Attachment metadata 완료. History 조회, Notification, Domain Event side effect, Admin 조회 남음 |
| 6 | Docker 통합 | 부분 완료. 로컬 Compose와 image build는 있음. 전체 smoke gate 정리 남음 |
| 7 | 전통 AWS 배포 (EC2) | 예정 |
| 8 | AWS managed service 연동 | 예정 |
| 9 | CI/CD | 예정. CI는 있음, CD 없음 |
| 10 | Production hardening | 예정 |
| 11 | 서버리스 마이그레이션 | 예정. 착수 전에 Proposed ADR과 승인이 필요하다 |

## Phase 0 — Foundation 확정

Branch: `chore/foundation`. Harness, architecture, frozen specification, CI, Docker, Git workflow, ADR, test foundation을 확정한다.

## Phase 1 — Authentication

Branch: `feature/authentication`. User, Role, password hash, signup/login, JWT access token, refresh token, logout, `GET /api/v1/me`, Spring Security를 구현한다. 이때부터 IntelliJ를 본격 사용한다. IntelliJ는 code reading/debug/breakpoint/JPA/test 실행, Codex는 구현/test/diff review를 담당한다.

## Phase 2 — 구조 데이터

Branches: `feature/manage-dormitory-structure`, `feature/manage-residence`. Dormitory, Building, Space, Facility, MaintenanceCategory, Residence를 구현한다. SQL, Flyway, JPA, PostgreSQL을 본격 사용하고 Docker는 우선 PostgreSQL local development에 사용한다.

## Phase 3 — 신고 핵심

Branches: `feature/create-maintenance-request`, `feature/request-query-and-update`, `feature/request-attachments`. MaintenanceRequest aggregate, `@Version`, query/update, pagination, attachment metadata, S3 abstraction, presigned URL contract를 구현한다. 실제 S3 연결은 뒤로 미룬다.

## Phase 4 — 배정 / 상태전이

Branches: `feature/assign-maintenance-worker`, `feature/maintenance-lifecycle`. MaintenanceAssignment, transaction, optimistic lock, partial unique index, authorization, state machine을 구현한다. `assign()`, `start()`, `hold()`, `resume()`, `resolve()`, `reopen()`, `close()`의 전체 흐름을 IntelliJ Debugger로 추적한다. 새 worker는 반드시 다시 `/start`한다.

## Phase 5 — Workflow 완성

Branches: `feature/manage-maintenance-visits`, `feature/maintenance-communication`, `feature/request-history-notifications`, `feature/admin-dashboard`. MaintenanceVisit, WorkLog, Comment, RequestHistory, Notification, Domain Event, AFTER_COMMIT, Dashboard를 구현한다. Visit 완료는 Request resolve가 아니며 frozen permission/state를 유지한다.

남은 V1 범위(2026-11-27 기준, Frozen API와 구현 대조):

- `GET /maintenance-requests/{id}/history` 조회. RequestHistory 행은 이미 기록되지만 조회 경로가 없다.
- Notification 경로 넷(`GET /notifications`, `PATCH /notifications/{id}/read`, `POST /notifications/read-all`, `GET /notifications/unread-count`)과 AFTER_COMMIT domain event side effect. Notification DB write는 별도 transaction이다.
- Admin `GET users`, `GET dashboard summary`. Frozen API가 이름만 언급하고 DTO가 없으므로 계약 공백 조사와 Proposed ADR이 먼저다.

## Phase 6 — Docker 통합

Branch: `chore/local-production-hardening`. Spring Boot container와 PostgreSQL container를 `docker compose up`으로 실행한다. Unit, Integration, Testcontainers, API, Security, ArchUnit, Docker smoke gate를 모두 통과시킨다. Production profile은 Flyway 자동 실행을 끄므로, 배포 전에 별도 migration 실행 수단(일회성 container 또는 release 단계)을 정한다.

## Phase 7 — 전통 AWS 배포 (EC2)

Branch: `chore/ubuntu-deployment`. [ADR-010](../adr/ADR-010-ubuntu-ec2.md)의 경로(HTTPS → Ubuntu EC2 Nginx → Docker Spring Boot)를 실제로 띄운다. 처음에는 콘솔과 SSH로 수동 배포해 각 구성 요소를 직접 이해한다.

- EC2 Ubuntu LTS, Docker Engine/Compose, Nginx, systemd(`infra/` template 사용)
- 도메인과 TLS(Let's Encrypt 또는 ACM + 앞단 구성은 이 phase에서 결정)
- SSH, apt, systemctl, journalctl, docker logs, ss, curl, environment, restart/reboot recovery를 직접 검증한다.
- `/etc/dormfix/app.env`(mode 0600), JWT 키, DB 비밀번호는 checkout 밖에 둔다.
- 완료 기준: HTTPS로 liveness/readiness와 주요 API smoke가 통과하고, 재부팅 뒤 자동 복구된다.
- 비용: 시작 전에 Free Tier 범위와 예상 월 비용을 확인하고, 실습이 끝나면 중지/삭제 절차를 기록한다.

## Phase 8 — AWS managed service 연동

Branch: `chore/aws-infrastructure`. EC2 Ubuntu, RDS PostgreSQL, S3, IAM, CloudWatch를 사용한다.

- VPC, public/private subnet, Security Group(앱은 Nginx만, DB는 앱 SG만 허용)
- RDS PostgreSQL 17: encryption, 자동 backup/PITR, verify-full TLS, DDL/DML 계정 분리
- S3 private bucket과 실제 `AttachmentStorage` S3 adapter(presign·head·download). 현재 fail-closed adapter를 대체하는 별도 feature slice다.
- EC2 instance role로 S3 prefix와 secret 조회만 허용(IAM 최소 권한). Secret은 SSM Parameter Store 또는 Secrets Manager.
- CloudWatch Logs/metrics/alarm
- 인프라 구성 방식(콘솔 수동 vs Terraform 등 IaC)은 이 phase 시작 시 결정한다. 수동으로 한 번 구성한 뒤 IaC로 옮기는 순서를 권장한다.

## Phase 9 — CI/CD

Branch: `chore/continuous-deployment`. Feature/PR에서는 build, test, ArchUnit, Checkstyle, Docker build를 수행한다. main merge 후 image build → registry push(ECR 또는 Docker Hub, `chore/dockerhub-publish` 참고) → migration → deploy → health check → rollback 전략을 적용한다. GitHub Actions는 OIDC로 AWS에 접근하고 장기 access key를 쓰지 않는다.

## Phase 10 — Production hardening

Branch: `chore/production-hardening`. TLS, IAM 최소권한, Security Group, DB backup/restore 리허설, health/readiness, structured log, trace ID, CloudWatch, rollback, failure recovery, load/security test를 다룬다. 장애 상황을 Ubuntu에서 직접 추적한다. Phase 11의 비교 기준이 되도록 이 시점의 응답 시간, 비용, 운영 부담을 기록한다.

## Phase 11 — 서버리스 마이그레이션

Branch: `chore/serverless-migration`(ADR 승인 후 세부 branch로 나눈다). Phase 7~10에서 운영한 EC2 구성을 서버리스로 옮기고 두 방식을 비교한다. 이 phase는 ADR-010(첫 배포는 EC2)과 ADR-009(V1 event, SQS 없음)의 이후 단계이므로, 착수 전에 증거 → Proposed ADR → 대안·장단점 → 사용자 승인 순서를 따른다. 승인 전에는 기존 baseline이 유효하다.

ADR에서 결정할 항목:

- Compute: AWS Lambda(Java 21, SnapStart, API Gateway, Spring Boot는 `aws-serverless-java-container` 등으로 감싸기) vs ECS Fargate(container 그대로, 서버 관리만 제거). Cold start, 연결 수, 코드 변경량, 비용을 비교한다.
- Database: RDS 유지 + RDS Proxy(Lambda 연결 폭주 방지) vs Aurora Serverless v2.
- Migration: Flyway를 별도 Lambda 또는 CI 단계에서 실행한다. 요청 처리 함수에서 migration을 돌리지 않는다.
- 비동기 처리: Notification side effect를 EventBridge/SQS로 옮길지. 옮긴다면 V2 Outbox 설계와 함께 다룬다.
- 정적 frontend(있다면) S3 + CloudFront, 인증·CORS 경계 재검토.
- IaC: 서버리스 구성은 AWS SAM, CDK, Terraform 중 하나로 코드화한다.

진행 원칙: domain/application 코드는 바꾸지 않고 진입점과 infrastructure adapter만 교체하는 것을 목표로 한다. 전환은 EC2와 병행 운영 → smoke·부하 비교 → 트래픽 전환 → EC2 정리 순서로 하고, 결과(비용, latency, cold start, 운영 부담)를 문서로 남긴다.

## 도구 사용 시점

Codex와 Git/GitHub는 전 phase, IntelliJ와 Java 21/Spring Boot는 Phase 1부터, SQL/Flyway는 Phase 2부터 사용한다. Docker는 초기 PostgreSQL local 중심으로 사용하다가 Phase 6부터 application+DB 전체 container로 전환한다. Bash/Linux CLI는 Phase 6 이후, Ubuntu EC2는 Phase 7, RDS/S3/IAM/CloudWatch는 Phase 8, GitHub Actions CD는 Phase 9부터 본격 적용한다. Lambda/API Gateway 또는 Fargate, RDS Proxy, IaC 도구는 Phase 11에서 다룬다. AWS SQS는 V2 Outbox 또는 Phase 11 ADR에서만 검토한다.

V2는 `Spring Event → Outbox → SQS → Worker → Notification` 확장과 선택적 AI 분류·추천·중복탐지·반복고장 분석으로 한정한다. AI는 V1 dependency가 아니다.
