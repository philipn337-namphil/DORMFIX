# CI와 향후 CD

Pull Request와 feature branch에서는 Repository Guard, Gradle build/test, ArchUnit, Checkstyle, integrationTest, Docker build를 실행한다. Main merge 뒤에는 immutable image를 만들고 승인된 registry와 protected environment를 통해 Ubuntu에 배포하며 health/readiness와 rollback을 검증한다.

배포 기록에는 image digest, Flyway version, 결과를 남긴다. Health 실패 시 schema-compatible previous image만 선택하고, migration이 incompatible하면 reviewed forward fix/restore decision을 사용한다. 자동 blind schema rollback은 금지한다. OIDC/short-lived credential, environment protection, serialized deployment, auditable operator access를 사용하며 실제 CD는 runbook 검증 후 구현한다.
