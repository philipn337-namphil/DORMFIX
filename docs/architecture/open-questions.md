# Architecture review register - 미해결, baseline 보존

이 문서의 항목은 human owner가 명시적으로 승인하기 전까지 OPEN이다. Gap이나 긴장 관계를 기록한 것이며 V1을 재설계할 권한이 아니다. Historical context와 충돌하면 현재 foundation prompt와 Frozen product가 우선한다. Phase 0에서는 이를 해결하기 위한 business DDL/feature를 구현하지 않는다.

각 질문은 소유 feature slice에서 evidence와 함께 검토하고, 필요하면 proposed ADR로 올린다. 승인 전에는 기존 state, permission, ERD, API semantics를 유지한다.

## Resolved authentication decisions

2026-09-11 human decision으로 Authentication Phase 1의 다음 항목을 확정했다. 상세 refresh-token 결정은 [ADR-013](../adr/ADR-013-refresh-token-storage.md)에 기록한다.

- Role은 `Set<Role>`로 모델링하고 `user_roles(user_id, role)`에 저장한다.
- 공개 signup에서 선택 가능한 role은 `RESIDENT`뿐이다. 일반 사용자가 privileged role을 선택하는 것은 금지한다.
- Access token은 JWT이며 수명은 30분이다.
- Refresh token은 opaque random token이며 수명은 14일이다.
- Refresh token 원문은 저장하지 않고 PostgreSQL에 SHA-256 hash만 저장한다.
- Refresh마다 rotation하고, old token 재사용은 거부한다.
- Logout은 해당 refresh token을 revoke한다.
- `SUSPENDED`와 `WITHDRAWN` 사용자는 login과 refresh를 거부한다.
- Redis와 token family는 V1에서 사용하지 않는다.

2026-09-13 human decision으로 Authentication Phase 1의 다음 세부사항을 추가 확정했다.

- Refresh token table은 `refresh_token_sessions`이며 columns는 `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at`이다. `token_hash`는 unique이고 raw refresh token은 저장하지 않는다.
- Rotation은 기존 row revoke 후 새 row 생성으로 처리한다. Token family와 `replaced_by` 같은 구조는 V1에서 사용하지 않는다.
- Access token은 `Authorization: Bearer <JWT>` header로 전달한다.
- Refresh token은 login response의 JSON body로 반환하고 refresh/logout 요청의 JSON body로 전달한다. V1에서는 cookie를 사용하지 않는다.
- Signup email은 trim 후 lowercase로 normalize한다.
- Signup은 role과 status를 입력받지 않고 서버가 각각 `RESIDENT`와 `ACTIVE`를 부여한다.
- Signup의 `name`, `email`, `password`는 필수이고 `phone`, `studentNumber`는 선택이다. `studentNumber`는 값이 존재할 경우 unique다.

위 결정으로 refresh-token 물리 table/column, rotation persistence, token 전달 방식, signup 입력·기본값·email normalization 질문은 resolved 되었다. Dormitory scope 저장 방식은 이번 Phase에서 확정하지 않으며 계속 OPEN이다. 이 결정은 임의의 추가 auth semantics를 허용하지 않는다.
