# ADR-008: Private S3 presigned attachment

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Binary upload가 application database transaction을 붙잡거나 불필요하게 Java server를 거쳐서는 안 된다.

## Decision / 결정

Presign을 인가하고 S3로 직접 upload한 뒤, 다시 인가·검증하고 metadata를 등록한다. Download도 인가된 단기 URL을 쓴다.

## Alternatives / 대안

모든 binary를 API로 중계하면 부하가 커진다. Public bucket은 resource 접근 규칙을 위반한다.

## Trade-offs / 장단점

Upload가 고아로 남을 수 있다. 용량 제한, content 검증, cleanup은 attachment 슬라이스에서 명시적인 정책이 필요하다. PUT header만으로는 안전한 content와 크기를 증명할 수 없다.

## Consequences / 결과

Key와 metadata만 저장한다. 최소 권한 IAM을 쓰고 signed URL은 로그에 남기지 않는다. Foundation 단계에서 AWS 구현을 추가하지 않는다.
