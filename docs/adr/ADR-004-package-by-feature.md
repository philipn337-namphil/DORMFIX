# ADR-004: Package by feature

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Vertical slice는 전역 기술 계층을 훑지 않고도 찾을 수 있어야 한다.

## Decision / 결정

identity/location/catalog/maintenance/notification을 쓰고 각각 api/application/domain/infrastructure를 둔다. 기술적 접착 코드는 작은 platform package가 담는다.

## Alternatives / 대안

전역 controller/service/repository package는 소유권을 흐린다. Aggregate마다 Gradle module을 두는 것은 불필요한 build 의식을 만든다.

## Trade-offs / 장단점

JPA annotation은 domain object에 있을 수 있고, 기술적 repository는 필요할 때 좁은 port 뒤의 adapter가 된다. 중복 entity model을 의무화하지 않는다.

## Consequences / 결과

ArchUnit이 API/domain/application 방향과 feature 간 cycle을 강제한다. Feature 간 협력은 application 계약이나 ID event를 쓰고 리뷰를 거친다.
