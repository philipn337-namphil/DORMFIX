# DormFix 문서 권위 체계

현재 Frozen V1 specification은 2026-09-10 foundation 요청을 기준으로 기록했다. Historical context는 별도로 보존하며 오래된 제안을 포함할 수 있다. Engineering architecture는 human baseline review 대상이고 product semantics는 이미 frozen이다. Open proposal은 어느 권위도 덮어쓰지 않는다.

## Product - authoritative frozen contract

- [Problem definition](product/problem-definition.md)
- [V1 scope](product/v1-scope.md)
- [ERD and complete column/enum inventory](product/erd-v1.md)
- [API inventory, versions and errors](product/api-spec-v1.md)
- [Permission/state/visit/retention rules](product/permission-state-matrix-v1.md)

## Architecture

- [Overview](architecture/overview.md), [backend packages](architecture/backend-architecture.md)
- [Aggregates](architecture/aggregate-boundaries.md), [transactions](architecture/transaction-boundaries.md), [events](architecture/domain-events.md)
- [Database/JPA](architecture/database.md), [security](architecture/security.md), [observability](architecture/observability.md)
- [AWS/Ubuntu deployment](architecture/deployment.md)
- [Unresolved review register](architecture/open-questions.md), [ADRs](adr/README.md)

## Development

- [Conventions](development/coding-conventions.md), [testing](development/testing-strategy.md)
- [Git/PR workflow](development/git-workflow.md), [Definition of Done](development/definition-of-done.md)
- [Local setup](development/local-development.md), [CI/CD](development/ci-cd.md)
- [Vertical roadmap](development/roadmap.md), [validation record](development/foundation-validation.md)

## Operations

- [Ubuntu deployment](operations/ubuntu-deployment.md)
- [Rollback](operations/rollback.md)
- [Troubleshooting](operations/troubleshooting.md)

## 출처와 enforcement

context/의 원본 파일은 변경하지 않는다. 이는 historical reference이며 agent 지침이나 폐기된 architecture를 복원할 권한이 아니다. Root/scoped AGENTS는 상세 권위 문서를 가리킨다. `scripts/check_repository.py`는 필수 파일, 상대 문서 link, frozen-product hash, wrapper checksum, 주요 configuration control을 검사한다. Product hash 갱신은 승인된 baseline 변경일 때만 수행한다. Hash는 review tripwire이지 위조 방지 governance가 아니다. ArchUnit, Checkstyle, API test, Testcontainers, CI와 human review가 함께 enforcement를 제공한다.
