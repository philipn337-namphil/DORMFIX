# Security 설계와 구현 경계

Foundation에서는 liveness/readiness probe만 anonymous이고 business traffic은 deny한다. HTTP Basic, form login, generated user와 token 발급/검증은 비활성이다. CSRF 비활성은 이 stateless cookie-free 경계에만 해당하며 CORS origin은 기본적으로 열지 않는다.

Phase 1에서는 Spring Security JWT/resource-server primitive로 Bearer access를 검증한다. Access token은 `Authorization: Bearer <JWT>` header로 전달한다. JWT는 signature, issuer, audience, `exp`, `nbf`, algorithm allow-list를 검증하고 access token 수명은 30분으로 한다. 현재 issuance slice는 key ID가 있는 RS256 signature를 사용하고 issuer, audience, X.509 DER public key와 PKCS#8 DER private key의 Base64 값을 환경에서 주입한다. rotating asymmetric key와 bounded clock skew를 적용하며, bespoke JWT parser나 검증되지 않은 claim의 role을 사용하지 않는다.

Password는 adaptive salted BCrypt 또는 검토된 Argon2 `PasswordEncoder`로 저장한다. plaintext/reversible password와 login payload log를 금지한다. Signup은 `name`, `email`, `password`를 필수로 받고 `phone`, `studentNumber`는 선택으로 받는다. Email은 trim 후 lowercase로 normalize하며, `studentNumber`는 값이 존재할 경우 unique다. Signup 입력으로 role과 status를 받지 않고 서버가 각각 `RESIDENT`와 `ACTIVE`를 부여한다. Role은 `Set<Role>`와 `user_roles(user_id, role)` 저장 방식을 사용한다.

Refresh token은 opaque random token으로 14일 동안 유효하다. `refresh_token_sessions` table은 `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at` columns를 가지며 `token_hash`는 unique다. PostgreSQL에는 SHA-256 hash만 저장하고 raw refresh token은 저장하지 않는다. Rotation 시 기존 row를 revoke한 후 새 row를 생성하며, logout 시 해당 row를 revoke한다. Token family와 `replaced_by` 같은 연결 구조 및 Redis는 V1에서 사용하지 않는다.

Refresh token은 login response의 JSON body로 반환하고 refresh/logout 요청의 JSON body로 전달한다. V1 authentication에서는 cookie를 사용하지 않는다. `SUSPENDED`와 `WITHDRAWN` 사용자는 login과 refresh를 거부한다. 상세 persistence 결정은 [ADR-013](../adr/ADR-013-refresh-token-storage.md)을 따른다.

Authorization은 authentication + role + ownership/current assignment/dormitory scope + current state를 application에서 검증한다. Entity binding mass assignment, 임의 status 변경, STAFF_ONLY 노출, former worker write를 허용하지 않는다. CORS는 명시적 origin/method/header만 사용하고 wildcard credential를 금지한다. Production은 HTTPS, trusted proxy, private RDS verify-full TLS, EC2 IAM role, Secret Manager/SSM을 사용하며 local `.env`는 Git에서 제외한다.

Log에는 password, token, consent, private comment, file content, presigned URL을 남기지 않는다. Error에는 SQL, stack trace, signing detail을 노출하지 않는다.
