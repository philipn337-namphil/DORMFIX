# ADR-006: Request optimistic locking

Status: Selected for foundation review (engineering baseline; product requirements remain frozen)

Date: 2026-09-10

## Context / 배경

동시에 움직이는 resident, worker, administrator가 서로의 변경을 덮어쓰거나 active assignment를 여러 개 만들어서는 안 된다.

## Decision / 결정

MaintenanceRequest에 JPA @Version을 쓰고, command에 submitted version을 필수로 요구하며, 불일치는 409 VERSION_CONFLICT로 응답한다. Active assignment는 PostgreSQL partial unique index로 보강한다.

## Alternatives / 대안

검증 없는 last-write-wins는 변경을 잃는다. 전면적인 pessimistic locking은 근거 없이 경합만 늘린다.

## Trade-offs / 장단점

Client는 conflict 후 다시 읽어 조정해야 한다. Flush 시점의 optimistic locking 없이 사전 검사만으로는 충분하지 않다.

## Consequences / 결과

Command, assignment, history 변경은 원자적이다. Integration test는 assignment 슬라이스와 함께 경합하는 transaction과 제약 위반을 다룬다.
