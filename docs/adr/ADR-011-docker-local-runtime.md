# ADR-011: Docker and local Compose

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Developers need reproducible PostgreSQL and an application runtime matching deployment conventions.

## Decision / 결정

Use a multi-stage Java 21 image, non-root runtime, readiness health check and local Compose with loopback ports and persistent DB volume. Production uses an immutable prebuilt image and RDS.

## Alternatives / 대안

Host-only setup increases drift. Packaging a production DB on the app host conflicts with RDS architecture.

## Trade-offs / 장단점

Docker daemon is required for integration/image validation; local DB owner credential is only for development.

## Consequences / 결과

Do not hardcode secrets. Production Compose is separate, read-only where practical, with log rotation and restart policy. No down -v in ordinary recovery.
