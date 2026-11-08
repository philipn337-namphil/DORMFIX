# ADR-009: 지금은 Spring event, 이후 Outbox/SQS

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

RequestHistory는 durable해야 하지만, V1 notification side effect는 내부 event를 쓸 수 있다.

## Decision / 결정

Application은 command transaction 안에서 ID와 value를 담은 event를 발행하고, side-effect listener는 AFTER_COMMIT에 실행한다. Notification 기록은 별도의 REQUIRES_NEW service를 쓴다. V2 outbox/SQS는 경계만 문서화한다.

## Alternatives / 대안

Transaction 안에서 동기 외부 I/O를 하는 것은 안전하지 않다. 지금 outbox를 구현하는 것은 정해진 V1 방향을 위반한다.

## Trade-offs / 장단점

내부 event는 crash 시 유실 구간이 있다. Listener 실패가 command를 rollback하지 못하므로, 전달 허용 수준은 notification 출시 전에 승인되어야 한다.

## Consequences / 결과

Exactly-once나 durable delivery를 주장하지 않는다. Audit history는 핵심 변경과 함께 commit된다. V2에서 outbox publisher, 재시도, idempotent consumer를 추가할 수 있다.
