# Testing policy와 필수 gate

새 business logic에는 Domain unit, Application/use-case, Repository integration, API/Security test를 적절히 추가한다. PostgreSQL fidelity가 필요한 검증은 Testcontainers를 사용하고 ArchUnit은 package dependency를 검사한다. Checkstyle은 `check`의 merge gate다.

`backend/gradlew check bootJar`는 unit/API/ArchUnit/Checkstyle을 실행하고 `integrationTest`는 Docker/Testcontainers를 이용하는 별도 REQUIRED gate다. Docker가 없을 때 test를 disabled/ignored로 바꾸지 않는다. Foundation test는 실제 business behavior의 부재를 증명한다고 주장하지 않는다.

Transaction test는 실제 commit과 AFTER_COMMIT을 검증하고 test-wide rollback으로 가리지 않는다. Concurrent command는 별도 transaction/connection과 deterministic synchronization을 사용한다. 실패한 command는 history와 side effect를 남기지 않아야 하며 Notification은 별도 transaction으로 commit되어야 한다. Infra 변경 전에는 Compose config와 Docker image build를 검증한다. Guard regression test는 `python -m unittest discover -s scripts -p 'test_*.py'`로 실행한다.
