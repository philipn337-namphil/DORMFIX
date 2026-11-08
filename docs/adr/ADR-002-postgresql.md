# ADR-002: PostgreSQL

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Frozen ERD는 TIMESTAMPTZ, JSONB, 관계 무결성, 그리고 실용적인 partial unique index를 요구한다.

## Decision / 결정

local과 Testcontainers에는 PostgreSQL 17을 쓰고, 배포에는 호환되는 지원 버전의 RDS PostgreSQL을 쓴다. 고정한 postgres:17.6-alpine에서 시작하고 patch 갱신은 리뷰를 거친다.

## Alternatives / 대안

H2는 제약, 시간, JSONB를 충실히 재현하지 못한다. MySQL은 DB 역량 자체를 바꾼다. Document database는 승인된 모델을 약화시킨다.

## Trade-offs / 장단점

실제 integration test에는 Docker가 필요하다. Assignment 동시성과 migration 충실도를 생각하면 이 비용은 정당하다.

## Consequences / 결과

Schema 제약과 index는 PostgreSQL을 대상으로 테스트한다. RDS major/patch 호환성은 provisioning 전에 확인하며, 지금 cloud instance를 만들지는 않는다.
