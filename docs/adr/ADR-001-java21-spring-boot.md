# ADR-001: Java 21과 Spring Boot

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

이 프로젝트는 검증된 web, security, JPA, validation 통합을 갖춘 Java backend engineering을 중심에 둔다.

## Decision / 결정

Java 21과 Spring Boot 3.5.16을 Gradle 8.14.3과 함께 사용한다. Wrapper를 고정하고 배포본의 SHA-256을 검증한다. 의존성 버전을 맞추기 위해 Boot BOM을 사용한다.

## Alternatives / 대안

Boot 4.x는 업그레이드 선택지이지만 foundation에 필요하지는 않다. 순수 servlet stack은 통합 작업이 더 많이 들고, Java가 아닌 backend는 이 baseline과 충돌한다.

## Trade-offs / 장단점

3.5 라인을 택하면 foundation 단계의 변경 churn이 최소화된다. 배포 전에 patch와 지원 상태를 검토해야 하며, 버전을 고정했다는 것이 무기한 지원을 보장한다는 뜻은 아니다. Java 21은 toolchain을 통해 compile과 test를 수행한다.

## Consequences / 결과

backend Gradle build를 하나로 유지하고 버전 변경은 리뷰를 거친 PR에 기록한다. 주 참고 문서: https://docs.spring.io/spring-boot/3.5/system-requirements.html
