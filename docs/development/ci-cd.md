# CI와 향후 CD

Pull Request와 feature branch에서는 Repository Guard, Gradle build/test, ArchUnit, Checkstyle, integrationTest, Docker build를 실행한다. `main` push에서는 이 gate가 모두 성공한 뒤 `backend/Dockerfile`을 다시 빌드해 Docker Hub의 `namphil/dormfix`에 `latest`와 `sha-<full-commit-sha>` tag를 push한다. PR과 non-main branch는 registry login이나 push를 수행하지 않는다.

Docker Hub 인증에는 repository secret `DOCKERHUB_TOKEN`만 사용한다. 값은 Docker Hub account password가 아니라 `namphil/dormfix`에 push할 수 있는 최소 권한 access token이어야 하며 저장소, 로그, artifact에 기록하지 않는다. 배포는 mutable `latest`가 아니라 workflow가 기록한 image digest 또는 `sha-<full-commit-sha>` tag를 고정해 사용한다.

실제 Ubuntu 배포 자동화는 아직 구현하지 않는다. 배포 기록에는 image digest, Flyway version, 결과를 남긴다. Health 실패 시 schema-compatible previous image만 선택하고, migration이 incompatible하면 reviewed forward fix/restore decision을 사용한다. 자동 blind schema rollback은 금지한다. OIDC/short-lived credential, environment protection, serialized deployment, auditable operator access를 사용하며 실제 CD는 runbook 검증 후 구현한다.
