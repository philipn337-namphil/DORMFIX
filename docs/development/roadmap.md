# Vertical implementation roadmap

Phase 0은 foundation only다. 각 phase는 [Definition of Done](definition-of-done.md)을 적용하며 API → Application → Domain → Persistence → Migration → Test → 필요한 Documentation을 포함하는 vertical slice로 개발한다. Layer 기반 branch는 금지한다.

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

## Phase 6 — Docker 통합

Branch: `chore/local-production-hardening`. Spring Boot container와 PostgreSQL container를 `docker compose up`으로 실행한다. Unit, Integration, Testcontainers, API, Security, ArchUnit, Docker smoke gate를 모두 통과시킨다.

## Phase 7 — Ubuntu 실배포

Branch: `chore/ubuntu-deployment`. AWS EC2 Ubuntu, Nginx, Docker, Spring Boot에서 SSH, apt, systemctl, journalctl, docker logs, ss, curl, environment, TLS, restart/reboot recovery를 직접 검증한다. 자동배포 전에 수동배포를 수행한다.

## Phase 8 — AWS 연동

Branch: `chore/aws-infrastructure`. EC2 Ubuntu, RDS PostgreSQL, S3, IAM, CloudWatch를 사용한다. VPC, Security Group, IAM Role, subnet, secret management, backup을 검토하며 local V1 완료 전에는 본격 연결하지 않는다.

## Phase 9 — CI/CD

Branch: `chore/continuous-deployment`. Feature/PR에서는 build, test, ArchUnit, Checkstyle, Docker build를 수행한다. main merge 후 image build → deploy → Ubuntu → health check → rollback 전략을 적용한다.

## Phase 10 — Production hardening / V2 준비

Branch: `chore/production-hardening`. TLS, IAM 최소권한, Security Group, DB backup, health/readiness, structured log, trace ID, CloudWatch, rollback, failure recovery, load/security test를 다룬다. 장애 상황을 Ubuntu에서 직접 추적한다.

## 도구 사용 시점

Codex와 Git/GitHub는 Phase 0~10, IntelliJ와 Java 21/Spring Boot는 Phase 1~10, SQL/Flyway는 Phase 2~10에서 사용한다. Docker는 초기 PostgreSQL local 중심으로 사용하다가 Phase 6부터 application+DB 전체 container로 전환한다. Bash/Linux CLI는 Phase 6 이후, Ubuntu는 Phase 7, AWS EC2는 Phase 7, RDS/S3/IAM/CloudWatch는 Phase 8, GitHub Actions CD는 Phase 9부터 본격 적용한다. AWS SQS는 V2/Phase 10 이후에만 Outbox와 함께 검토한다.

V2는 `Spring Event → Outbox → SQS → Worker → Notification` 확장과 선택적 AI 분류·추천·중복탐지·반복고장 분석으로 한정한다. AI는 V1 dependency가 아니다.
