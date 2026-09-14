# ADR-013: Refresh-token persistence gap

Status: Accepted - implementation pending

Date: 2026-09-10

## Context / 배경

Frozen endpoints require refresh/logout, but the 15-entity ERD has no refresh-token persistence or revocation model.

## Decision / 결정

Authentication 정책으로 다음을 확정한다.

- Refresh token은 암호학적으로 안전한 opaque random token이다.
- PostgreSQL table 이름은 `refresh_token_sessions`이다.
- Table columns는 `id`, `user_id`, `token_hash`, `expires_at`, `revoked_at`, `created_at`이다.
- `token_hash`는 unique다.
- 원문 refresh token은 저장하지 않고 SHA-256 hash만 저장한다.
- Refresh token의 수명은 14일이다.
- Refresh 요청마다 rotation하며 기존 row를 revoke한 후 새 row를 생성한다. 이미 사용된 old token은 거부한다.
- Logout은 해당 refresh-token row를 revoke한다.
- Token family와 `replaced_by` 같은 연결 구조는 V1에서 사용하지 않는다.
- Redis는 사용하지 않는다.
- Refresh token은 login response의 JSON body로 반환하며 refresh/logout 요청의 JSON body로 전달한다.
- V1에서는 refresh token 전달에 cookie를 사용하지 않는다.

이 ADR 갱신만으로 Java 코드나 Flyway migration을 추가하지 않는다.

## Alternatives / 대안

Pure stateless refresh tokens cannot provide strong immediate rotation/reuse/logout revocation alone. Redis, token family, `replaced_by` 구조는 V1에서 사용하지 않는다. Managed identity provider는 auth ownership을 변경하므로 별도 결정 대상이다.

## Trade-offs / 장단점

PostgreSQL 저장소와 revoke/rotation 상태는 refresh/logout을 지원하지만 추가 migration과 운영 고려가 필요하다. V1은 JSON body 전달을 사용하고 cookie/CSRF 선택은 하지 않는다.

## Consequences / 결과

Phase 1은 이 확정 정책을 구현 계약으로 사용한다. 구현 전까지 scaffold deny-all을 유지한다. Token family, `replaced_by`, Redis, 원문 refresh token 저장, cookie 전달은 V1에 포함하지 않는다.
