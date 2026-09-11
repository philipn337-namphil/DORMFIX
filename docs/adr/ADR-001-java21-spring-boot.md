# ADR-001: Java 21 and Spring Boot

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context

The project centers on Java backend engineering with proven web, security, JPA and validation integration.

## Decision

Use Java 21 and Spring Boot 3.5.16 with Gradle 8.14.3. Pin the wrapper and verify its distribution SHA-256. Use the Boot BOM for aligned dependencies.

## Alternatives

Boot 4.x is an upgrade option, not necessary for the foundation. Plain servlet stacks require more integration work; non-Java backends conflict with the baseline.

## Trade-offs

The selected 3.5 line minimizes foundation churn. Patch/support status must be reviewed before deployment; pinning does not claim indefinite support. Java 21 runs compile and tests through toolchains.

## Consequences

Maintain one backend Gradle build and record version changes in reviewed PRs. Primary reference: https://docs.spring.io/spring-boot/3.5/system-requirements.html
