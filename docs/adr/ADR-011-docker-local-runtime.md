# ADR-011: Docker와 local Compose

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

개발자에게는 재현 가능한 PostgreSQL과 배포 관례에 맞는 application runtime이 필요하다.

## Decision / 결정

Multi-stage Java 21 image, non-root runtime, readiness health check를 쓰고, local Compose는 loopback port와 영속 DB volume을 쓴다. Production은 미리 빌드한 immutable image와 RDS를 쓴다.

## Alternatives / 대안

Host만 쓰는 구성은 환경 drift를 키운다. Production DB를 app host에 묶는 것은 RDS 아키텍처와 충돌한다.

## Trade-offs / 장단점

Integration과 image 검증에는 Docker daemon이 필요하다. Local DB owner credential은 개발용으로만 쓴다.

## Consequences / 결과

Secret을 하드코딩하지 않는다. Production Compose는 분리하고 가능한 곳은 read-only로 두며 log rotation과 restart policy를 설정한다. 통상 복구에서 down -v는 쓰지 않는다.
