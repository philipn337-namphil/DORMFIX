# ADR-010: 첫 배포는 Ubuntu EC2

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

실제 Linux 운영을 익히는 것이 이 프로젝트의 목표이고, 승인된 배포 경로는 AWS를 쓴다.

## Decision / 결정

Host Nginx, Docker app, private RDS, S3로 구성한 Ubuntu EC2를 계획한다. Foundation 동안에는 아무것도 provisioning하지 않는다.

## Alternatives / 대안

Managed ECS는 나중에 쓸 만하지만 초기 server 운영 학습의 일부를 건너뛰게 한다. Kubernetes는 규모에 맞지 않는다.

## Trade-offs / 장단점

단일 host 배포는 downtime과 가용성에 한계가 있다. Patch, backup, TLS 갱신, monitoring, 재부팅 복구는 운영자 책임이다.

## Consequences / 결과

Network, secret, migration, rollback runbook을 명시적으로 유지한다. ECR/ECS와 SQS는 이후 단계다.
