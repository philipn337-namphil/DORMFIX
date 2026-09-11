# DormFix

기숙사 시설 유지보수 Workflow: REPORT -> ASSIGN -> VISIT -> REPAIR -> CONFIRM.

이 저장소는 **engineering foundation만** 담는다. Java 21/Spring Boot 골격, PostgreSQL/Flyway 연결, 안전한 health/security/logging 경계, architecture check, CI, Docker 및 배포 템플릿을 제공한다. Business API, authentication, maintenance entity는 아직 구현하지 않는다. Frozen V1 계약은 [문서 색인](docs/README.md)에 있다.

## 로컬 시작

호스트 빌드용 Java 21, Linux container/Compose를 지원하는 Docker, repository check용 Python 3을 설치한다. 전역 Gradle 설치는 필요하지 않다.

1. Copy `.env.example` to `.env` and set a unique local `POSTGRES_PASSWORD`.
2. From this directory run:

```sh
docker compose config --quiet
docker compose up --build -d --wait --wait-timeout 180
curl --fail http://127.0.0.1:8080/actuator/health/liveness
curl --fail http://127.0.0.1:8080/actuator/health/readiness
```

로컬 PostgreSQL은 persistent volume과 loopback port 5432를 사용한다. 애플리케이션은 loopback 8080을 사용하고 시작 시 Flyway를 실행한다. authentication 구현 전에는 `/api/v1/me`가 401을 반환한다. 종료는 `docker compose down`을 사용하며 data를 보존한다. 일상적인 종료에 `down -v`를 사용하지 않는다. 자세한 내용은 [로컬 개발 가이드](docs/development/local-development.md)를 참조한다.

## 검증

```sh
python scripts/check_repository.py
cd backend
./gradlew check bootJar
./gradlew integrationTest
```

PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용한다. integration test에는 Docker가 필요하며 Docker가 없으면 skip하지 않고 실패해야 한다. 최초 빌드에서는 dependency를 다운로드한다. [검증 기록](docs/development/foundation-validation.md)은 실행된 check와 환경 차단 요인을 구분한다.

## 저장소 구조

| 경로 | 책임 |
|---|---|
| AGENTS.md | Root engineering harness와 필수 gate |
| backend/ | Spring Boot 애플리케이션 및 architecture/API/PostgreSQL test |
| frontend/ | 보류된 최소 client 경계 |
| infra/ | Production Compose, Nginx, systemd 템플릿 |
| docs/product/ | Frozen ERD/API/권한/state 규칙 |
| docs/architecture/, docs/adr/ | Architecture decision과 미해결 제안 |
| docs/development/, docs/operations/ | Workflow, roadmap, runbook |
| scripts/ | Repository 계약 check |
| .github/ | CI, dependency update, PR checklist |

코드 변경 전 [AGENTS.md](AGENTS.md)를 읽는다. technical-layer branch나 main에서의 직접 feature 개발 대신 vertical feature branch를 사용한다. 초기 로컬 branch는 `chore/foundation`이다. [검토 질문](docs/architecture/open-questions.md), [ADR](docs/adr/README.md), [roadmap](docs/development/roadmap.md)이 다음 slice 전에 검토할 내용을 정의한다.

Production template은 계획이며 배포된 시스템이 아니다. 목표 경로는 HTTPS -> Ubuntu EC2/Nginx -> Docker Spring Boot이고 private RDS/S3를 사용한다. [배포 architecture](docs/architecture/deployment.md)를 참조한다. 실제 secret, AWS resource, AI/V2 infrastructure는 포함하지 않는다.
