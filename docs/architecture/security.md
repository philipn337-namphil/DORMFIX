# Security 설계와 구현 경계

Foundation에서는 liveness/readiness probe만 anonymous이고 business traffic은 deny한다. HTTP Basic, form login, generated user와 token 발급/검증은 비활성이다. CSRF 비활성은 이 stateless cookie-free 경계에만 해당하며 CORS origin은 기본적으로 열지 않는다.

Phase 1에서는 Spring Security JWT/resource-server primitive로 Bearer access를 검증하고 issuer/audience/algorithm allow-list, expiry/not-before, bounded clock skew, rotating asymmetric key를 적용한다. bespoke JWT parser나 검증되지 않은 claim의 role을 사용하지 않는다. Token lifetime, suspension/revocation, signup eligibility는 security contract에서 승인한다.

Password는 adaptive salted BCrypt 또는 검토된 Argon2 `PasswordEncoder`로 저장한다. plaintext/reversible password, login payload log, user가 선택하는 privileged signup role을 금지한다. Refresh token은 ADR-013의 rotating opaque token/hash/reuse detection/revocable family 제안을 따르되 Frozen ERD와 승인 전에는 table·Redis를 추가하지 않는다.

Authorization은 authentication + role + ownership/current assignment/dormitory scope + current state를 application에서 검증한다. Entity binding mass assignment, 임의 status 변경, STAFF_ONLY 노출, former worker write를 허용하지 않는다. CORS는 명시적 origin/method/header만 사용하고 wildcard credential를 금지한다. Production은 HTTPS, trusted proxy, private RDS verify-full TLS, EC2 IAM role, Secret Manager/SSM을 사용하며 local `.env`는 Git에서 제외한다.

Log에는 password, token, consent, private comment, file content, presigned URL을 남기지 않는다. Error에는 SQL, stack trace, signing detail을 노출하지 않는다.
