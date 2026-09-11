# ADR-010: Ubuntu EC2 first deployment

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Learning real Linux operations is a project goal and the approved deployment path uses AWS.

## Decision / 결정

Plan Ubuntu EC2 with host Nginx, Docker app, private RDS and S3. Do not provision anything during foundation.

## Alternatives / 대안

Managed ECS may be useful later but bypasses some initial server operations learning. Kubernetes is disproportionate.

## Trade-offs / 장단점

Single-host deployment has downtime/availability limits; patching, backups, TLS renewal, monitoring and reboot recovery are operator responsibilities.

## Consequences / 결과

Keep network/secret/migration/rollback runbooks explicit. ECR/ECS and SQS are later phases.
