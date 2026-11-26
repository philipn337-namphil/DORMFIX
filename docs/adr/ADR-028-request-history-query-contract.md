# ADR-028: RequestHistory 조회 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-27

2026-11-27 사람 승인으로 이 계약을 제안대로 수락했다. Resident view에서 `COMMENT_DELETED` 행을 모두
빼는 것, raw metadata 대신 typed field로 투영하는 것, lifecycle reason 기록 공백을 같은 슬라이스에서
닫는 것을 포함한다.

## Context

Frozen API는 `GET /maintenance-requests/{id}/history`를 "Read permitted history"로만 적고, 같은 문서
끝에서 "public history projections require slice contracts"라고 공백을 명시한다. RequestHistory 행은
V6 이후 모든 command가 이미 기록하고 있지만 읽는 경로가 없다.

열린 질문은 다섯 가지다.

1. 누가 어떤 request의 history를 읽는가, 가시성 밖은 무엇으로 답하는가
2. Resident가 받으면 안 되는 행이 있는가
3. `metadata` JSONB를 그대로 내보내는가, 정해진 field로 투영하는가
4. 정렬과 페이징
5. 기존 구현 공백: ADR-022와 ERD V8 규칙은 "append-only RequestHistory가 각 전이의 reason을 보관한다"고
   정했지만, 현재 lifecycle command는 history `metadata`에 reason을 쓰지 않는다(`mark-duplicate`의
   `duplicateOfId`만 쓴다). `status_reason` column은 다음 전이가 덮어쓰므로 지난 reason은 지금 어디에도
   남지 않는다.

## Decision

### 가시성은 detail 조회와 같다

History를 읽을 수 있는 caller는 [ADR-020](ADR-020-maintenance-request-query-contract.md)의 read
가시성과 같다. Reporter인 `RESIDENT`, 배정되었거나 배정된 적 있는 `WORKER`, scope 안의 `ADMIN`,
`SUPER_ADMIN`이다. Former assignee는 historical read-only 규칙에 따라 읽을 수 있다. 가시성 밖의 request는
`404 REQUEST_NOT_FOUND`로 답하며, detail 조회와 같은 이유(순번 ID의 존재 oracle 차단)다. 판단은 기존
`RequestRelationshipResolver`를 재사용한다.

### Resident projection은 `COMMENT_DELETED`를 받지 않는다

[ADR-026](ADR-026-comment-contract.md)은 resident가 `STAFF_ONLY` comment의 존재를 row, 개수, 삭제
응답 어느 것으로도 알 수 없다고 정했다. `COMMENT_DELETED` 행은 `commentId`를 담으므로 그대로 주면
`STAFF_ONLY` comment의 삭제가 드러난다. Resident 관계로만 읽는 caller에게는 `COMMENT_DELETED` 행을
모두 빼고, 필터는 page를 적재한 뒤가 아니라 query에서 적용해 `totalElements`도 같은 기준으로 센다.
Staff 관계(administrator 또는 배정 이력)가 있는 caller는 모든 행을 받는다. 여러 관계를 가지면 staff
view가 우선한다(ADR-026과 같다).

나머지 event는 resident도 받는다. Assignment의 `workerId`는 detail의 `assigneeId`로 이미 보이고,
visit은 resident가 목록으로 볼 수 있으며, PATCH 행은 field 이름만 담고, reason은 detail의
`statusReason`으로 이미 보인다.

### `metadata`는 typed field로 투영한다

Raw JSONB 문자열을 응답에 싣지 않는다. 저장 형식이 바뀌면 계약이 따라 흔들리고, 앞으로 metadata에
들어갈 값이 검토 없이 공개되기 때문이다. 응답 DTO는 다음과 같다.

`RequestHistoryEntryResponse(id, eventType, actorId, previousStatus, newStatus, reason, workerId,
previousWorkerId, visitId, duplicateOfId, changedFields, commentId, createdAt)`

- 해당 event에 없는 field는 `null`이다(`changedFields`는 빈 배열 대신 `null`).
- `actorId`는 system action이면 `null`일 수 있다(ERD).
- `commentId`는 staff view에서만 채워진다. Resident view에는 해당 행이 없다.
- 새 metadata key가 생기면 이 DTO에 field를 추가하는 계약 변경으로 다룬다.

### Lifecycle reason을 history에 기록한다 (구현 공백 수정)

여덟 lifecycle command는 command가 reason을 담았을 때 history `metadata`에 `{"reason": "..."}`를
기록한다. `mark-duplicate`는 `{"duplicateOfId": <id>, "reason": "..."}`가 된다. Reason이 없으면
key를 쓰지 않는다. Reason은 사용자가 입력한 자유 문자열이므로 문자열 연결이 아니라 JSON
serializer로 escape한다. 이미 기록된 과거 행은 reason을 복원할 원천이 없으므로 backfill하지 않는다.

새 event type이나 column이 없으므로 migration은 필요 없다.

### 정렬과 페이징

`GET /maintenance-requests/{id}/history?page=&size=`

- 정렬은 `createdAt ASC, id ASC`로 고정한다. History는 시간 순서로 읽는 기록이고 comment 목록과 같다.
- `page`는 0부터, 기본값 `0`. `size`는 `1..100`, 기본값 `20`. 위반은 `400 VALIDATION_FAILED`.
- 응답은 기존 page envelope를 쓴다.
- 정렬은 native SQL의 `ORDER BY`에 두고 정렬 없는 `PageRequest`를 쓴다(기존 패턴).

### Error

`404 REQUEST_NOT_FOUND`, `400 VALIDATION_FAILED`, `401`은 모두 기존 frozen code다. 새 code는 없다.

## Alternatives

1. **Resident에게도 `COMMENT_DELETED`를 주되 `PUBLIC` comment의 삭제만 준다.** Resident가 자기 comment가
   지워진 사실을 history로 알 수 있다. 하지만 history와 comment table을 metadata JSONB 경로로 join해야
   하고, 그 필터가 틀리면 바로 `STAFF_ONLY` 존재가 새어 나간다. Resident는 comment 목록에서 이미 삭제
   결과를 본다. 이득에 비해 위험이 커서 택하지 않는다.
2. **`metadata`를 JSON 객체 그대로 내보낸다.** 구현은 가장 간단하지만 저장 형식이 곧 공개 계약이 되고,
   이후 metadata에 넣은 값이 검토 없이 노출된다.
3. **Reason 공백을 별도 fix 슬라이스로 분리한다.** 범위는 깔끔해지지만, reason이 없는 history 조회를
   먼저 공개하게 되고 그동안 reason이 계속 유실된다. 조회 계약과 기록 형식이 같은 DTO로 묶이므로 한
   슬라이스에서 닫는다.
4. **최신순(`createdAt DESC`) 정렬.** Request 목록과는 맞지만, history는 처음부터 읽는 기록이라 comment와
   같은 오름차순을 택한다.

## Consequences

- 승인되면 Frozen API specification에 DTO, 가시성, resident 필터, 정렬, 페이징을, permission matrix에
  history read 행을, ERD에 reason metadata 규칙을 동기화하고 baseline hash를 갱신한다.
- `RequestHistoryRepository`에 조회 method 둘(staff view, resident view)을 별도로 두어 resident view가
  `COMMENT_DELETED`를 받을 수 없게 구조적으로 막는다(범용 필터 parameter 없음).
- Lifecycle service 테스트에 reason 기록 회귀 테스트를 추가한다.
- 과거 행에는 reason이 없다. 응답의 `reason: null`은 "reason 없음"과 "기록 이전" 둘 다를 뜻한다.
