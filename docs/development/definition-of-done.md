# Definition of Done

Feature는 다음을 모두 만족할 때 human acceptance 대상이다.

- Scope와 frozen rule을 이해하고 관련 AGENTS/docs/code/test를 읽었다.
- Vertical branch에서 unrelated 변경 없이 작업했다.
- API command, DTO validation, error/status semantics, version concurrency가 contract와 일치한다.
- Authentication, role, ownership/current assignment/scope, state 검사를 구현하고 negative case를 테스트했다.
- Domain transition/invariant, consent snapshot, retention, terminal state를 보존했다.
- 필요한 Flyway migration, PostgreSQL constraint/mapping, upgrade path를 검토했다.
- State/history commit은 atomic이고 external effect는 commit 후에 수행한다.
- Business logic/regression/API/integration/architecture test가 통과한다.
- Repository Guard, Gradle check/bootJar/integrationTest, 관련 Docker gate가 통과한다.
- Test를 약화하지 않았고 TODO로 필수 동작을 숨기지 않았다.
- Entity leak, N+1, dependency cycle, secret/PII leak, permissive security default가 없다.
- 계약/architecture/operations 문서와 approved ADR을 갱신했다.
- Git diff, migration, CI, deployment impact와 한계를 review했다.

필수 check를 실행할 수 없으면 pass가 아니라 blocked로 기록한다. Phase 0 acceptance가 deferred feature code를 면제하지 않는다.
