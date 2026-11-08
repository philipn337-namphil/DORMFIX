# ADR-007: Flyway와 물리 mapping 관례

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Frozen logical column은 schema가 조용히 바뀌는 일 없이 재현 가능한 migration을 필요로 한다.

## Decision / 결정

Flyway가 권위이고 Hibernate는 validate만 한다. snake_case 이름, app_user table, BIGINT identity ID, CHECK 제약을 가진 string enum, Instant/TIMESTAMPTZ가 mapping 관례다. Phase 0에는 business DDL이 없는 foundation migration을 둔다.

## Alternatives / 대안

Hibernate update는 리뷰된 migration 이력을 남기지 않는다. Native PostgreSQL enum은 변경을 더 강하게 결합시키고 ordinal enum은 안전하지 않다. Liquibase는 여기서 요구사항에 근거한 이점을 주지 않는다.

## Trade-offs / 장단점

미정인 logical nullability, default, user_roles 세부는 각 소유 슬라이스에서 여전히 리뷰가 필요하다. 어떤 관례도 추가 business field를 허가하지 않는다.

## Consequences / 결과

적용된 migration은 immutable하다. Expand/contract를 쓰고 production migration 신원을 분리한다. Migration 기동과 향후 제약은 PostgreSQL에서 테스트한다.
