# ADR-003: 실용적 modular monolith

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

1인 개발자에게는 분산 운영 비용 없이 명시적인 경계와 로컬 transaction이 필요하다.

## Decision / 결정

Spring Boot 배포 단위 하나와 PostgreSQL database 하나를 쓰고, 코드는 feature가 소유하며 aggregate는 서로 독립이다.

## Alternatives / 대안

Microservice는 분산 transaction과 운영 부담을 끌어들인다. 구조가 없는 monolith는 workflow 불변식을 숨긴다.

## Trade-offs / 장단점

Package 경계를 지키려면 ArchUnit과 코드 리뷰가 필요하지만, aggregate를 넘나드는 command는 단순한 로컬 transaction으로 남는다.

## Consequences / 결과

Kubernetes, 분산 CQRS, 추측에 의한 service 분리, 범용 base-service 계층은 두지 않는다.
