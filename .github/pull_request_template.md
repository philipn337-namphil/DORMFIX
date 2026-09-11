## 문제와 결과 동작
- 어떤 문제를 해결하며 최종 동작은 무엇인가?
- Scope와 제외 범위는 무엇인가?

## 계약과 위험
- Frozen ERD/API/권한/state/transaction/event에 영향이 없는가?
- Authorization, migration, 보안, rollback 위험을 검토했는가?
- 관련 ADR 또는 review note가 있는가?

## 검증
- [ ] `python scripts/check_repository.py`
- [ ] `backend/gradlew check bootJar`
- [ ] `backend/gradlew integrationTest` (해당 시)
- [ ] Compose/image 검증 (infra 변경 시)
- [ ] `git diff --check`

## Review checklist
- [ ] Vertical slice 범위와 unrelated 변경을 확인했다.
- [ ] 테스트를 삭제·약화하지 않았다.
- [ ] Secret과 생성 산출물이 없다.
- [ ] 문서와 Definition of Done을 갱신했다.
