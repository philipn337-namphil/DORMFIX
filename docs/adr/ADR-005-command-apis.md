# ADR-005: 명시적 lifecycle command

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Frozen V1은 임의의 resource status 편집이 아니라 인가된 transition을 정의한다.

## Decision / 결정

명시적 command endpoint 전부와 제한된 metadata PATCH를 유지한다. Transition은 aggregate method가 통제한다.

## Alternatives / 대안

범용 CRUD status 변경은 frozen product model을 위반한다. Workflow engine은 지금 가치를 더하지 않는다.

## Trade-offs / 장단점

Endpoint와 service 이름이 늘어나는 대신 business 의도와 negative test가 드러난다.

## Consequences / 결과

SUPER_ADMIN override, terminal 재개, visit 완료를 resolution으로 간주하는 처리는 두지 않는다.
