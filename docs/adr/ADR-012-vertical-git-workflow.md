# ADR-012: Vertical feature branch와 1인 리뷰

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

Codex를 쓰는 1인 개발이라도 범위 통제와 리뷰 증거가 필요하다. 사람 owner가 한 명이어도 마찬가지다.

## Decision / 결정

feature/use-case 브랜치를 쓰고 API/application/domain/persistence/test/docs를 거치는 PR 형식 리뷰를 한다. Foundation은 chore/foundation을 쓴다. main에서 feature 개발을 하지 않는다.

## Alternatives / 대안

Layer 브랜치는 business behavior를 쪼개고 통합을 복잡하게 만든다. main에서 바로 개발하면 리뷰 gate를 잃는다.

## Trade-offs / 장단점

본인이 올린 PR은 GitHub에서 self-approval을 받지 못할 수 있다. 불가능한 리뷰 설정에 매달리지 말고 기록된 사람 리뷰 checklist와 필수 CI를 쓴다.

## Consequences / 결과

최종 수락은 사람이 소유한다. 자동 push나 자동 배포는 없다. Migration 순서를 보존하고, 진짜 baseline 변경에는 architecture 승인을 요청한다.
