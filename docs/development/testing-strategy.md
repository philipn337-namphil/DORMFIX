# Testing policy와 필수 gate

새 business logic에는 Domain unit, Application/use-case, Repository integration, API/Security test를 적절히 추가한다. PostgreSQL fidelity가 필요한 검증은 Testcontainers를 사용하고 ArchUnit은 package dependency를 검사한다. Checkstyle은 `check`의 merge gate다.

`backend/gradlew check bootJar`는 unit/API/ArchUnit/Checkstyle을 실행하고 `integrationTest`는 Docker/Testcontainers를 이용하는 별도 REQUIRED gate다. Docker가 없을 때 test를 disabled/ignored로 바꾸지 않는다. Foundation test는 실제 business behavior의 부재를 증명한다고 주장하지 않는다.

Transaction test는 실제 commit과 AFTER_COMMIT을 검증하고 test-wide rollback으로 가리지 않는다. Concurrent command는 별도 transaction/connection과 deterministic synchronization을 사용한다. 실패한 command는 history와 side effect를 남기지 않아야 하며 Notification은 별도 transaction으로 commit되어야 한다. Infra 변경 전에는 Compose config와 Docker image build를 검증한다. Guard regression test는 `python -m unittest discover -s scripts -p 'test_*.py'`로 실행하며, 문서만 바꾼 작업에서도 로컬 검증에 포함한다.

## Test-first 순서 (2026-11-28부터)

새 feature slice는 TDD로 진행한다. 승인된 ADR의 권한 표와 규칙을 먼저 실패하는 테스트로 옮기고(Red), 그 테스트를 통과시키는 만큼만 구현한 뒤(Green), 테스트를 초록으로 유지한 채 정리한다(Refactor). 규칙 판단은 Spring 없이 도는 순수 단위 테스트로 빠르게 고정하고, Testcontainers 통합 테스트는 HTTP·보안·SQL 경계를 확인한다. Red 단계의 실패 출력(컴파일 실패 포함)과 Green 결과는 WORKLOG에 남긴다.
