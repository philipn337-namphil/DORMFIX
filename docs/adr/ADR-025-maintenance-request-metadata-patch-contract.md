# ADR-025: MaintenanceRequest metadata PATCH 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-10

2026-11-10 사람 승인으로 이 계약을 제안대로 수락했다. `spaceId`를 PATCH로 바꾸지 않는 것, 두 role을
겸한 caller를 administrator로 해석하는 것, 값이 바뀌지 않은 PATCH가 `version`을 올리지 않는 것을
포함한다.

## Context

Frozen V1은 `PATCH /maintenance-requests/{id}`를 "Allowed metadata only, version required"로 두고
경계를 분명히 그었다. 임의 status를 받아서는 안 되고, resident는 priority, reporter, assignment를
설정하지 못하며, administrator는 resident의 entry consent를 변경하지 못한다. Entity를 binding하거나
임의 field map을 반영하지 말고 allow-list된 별도 DTO와 정책을 쓴다. `version`은 필수이고 불일치는
`409 VERSION_CONFLICT`다.

Permission matrix는 role별 범위를 준다. `RESIDENT`는 `REPORTED`에서만 수정하고, `ADMIN`은 active
request의 허용된 category, priority, location을 조정한다.

그리고 Frozen API specification은 남은 공백을 명시적으로 지목한다. "Exact patch field matrices are
a review item"이다. 이 ADR이 그 review다.

정하지 않은 것은 다음이다.

1. role별로 정확히 어떤 field를 바꿀 수 있는지
2. administrator에게 "active request"가 어떤 status 집합인지
3. `spaceId`를 바꿀 수 있는지. ADR-019는 생성 시 `spaceId`를 reporter의 current Residence ROOM으로
   묶었는데, administrator가 location을 조정하면 그 규칙이 어떻게 되는지
4. `facilityId`를 설정·변경·해제할 수 있는지
5. `categoryId`를 바꾸면 `priority`가 새 category의 default를 따라가는지
6. PATCH가 RequestHistory를 남기는지. ERD의 event 예시에는 `PRIORITY_CHANGED`와
   `CATEGORY_CHANGED`가 있지만 그 목록이 완전한 enum이 아니라고 적혀 있다
7. 부분 수정의 의미. 생략과 명시적 null의 구분, 그리고 nullable field의 해제 방법
8. `preferredVisitStart`/`preferredVisitEnd`의 both-or-neither 규칙이 수정에도 적용되는지

## Decision

### Role별 field matrix

| Field | RESIDENT | scoped ADMIN / SUPER_ADMIN |
|---|---|---|
| `title` | ✅ | ❌ |
| `description` | ✅ | ❌ |
| `entryPolicy` | ✅ | ❌ |
| `contactBeforeEntry` | ✅ | ❌ |
| `preferredVisitStart` / `preferredVisitEnd` | ✅ | ❌ |
| `categoryId` | ❌ | ✅ |
| `priority` | ❌ | ✅ |
| `facilityId` | ❌ | ✅ |

두 집합은 교집합이 없다. Resident는 자신이 신고할 때 쓴 서술과 출입 동의를 고치고, administrator는
운영 분류와 대상 설비를 고친다.

`status`, `reporterId`, `requestNumber`, `spaceId`, `duplicateOfId`, `statusReason`, lifecycle
timestamp, assignment 관련 field는 어떤 role도 PATCH로 바꿀 수 없다. DTO가 이들을 받지 않으므로
unknown field로 `400`이 된다.

Administrator가 `entryPolicy`와 `contactBeforeEntry`를 바꿀 수 없다는 것은 frozen 규칙을 그대로
구현한 것이다. 출입 동의는 resident의 것이고 administrator가 대신 바꾸지 않는다.

Resident가 `priority`를 바꿀 수 없다는 것도 frozen 규칙이다. 신고자가 자기 신고의 우선순위를
올리는 것은 운영 판단을 우회한다.

### 두 개의 DTO, 하나의 경로

Frozen 경로는 하나이고 frozen 지시는 allow-list된 별도 DTO를 쓰라는 것이다. 따라서 요청 본문을
caller의 role로 해석한다. `RESIDENT`만 가진 caller는 resident field만 담은 본문을 보내고,
administrator role을 가진 caller는 administrator field만 담은 본문을 보낸다. 상대 집합의 field가
본문에 있으면 unknown field로 `400 VALIDATION_FAILED`다.

이 방식은 field map을 반영하지 않는다. 각 집합이 자기 record로 역직렬화되고 unknown field를
거부한다.

`RESIDENT`와 administrator role을 동시에 가진 caller는 administrator로 해석한다. 더 넓은 운영
권한이 있는 쪽을 택하며, 그 caller가 자기 신고의 서술을 고치려면 role 없는 다른 경로가 아니라
운영자로서 category와 priority를 조정하게 된다. V1에서 한 사람이 두 역할을 겸하는 일은 예외적이고,
이 선택은 두 집합을 한 요청에 섞지 않는다는 원칙을 지킨다.

### Resident는 REPORTED에서만

`RESIDENT`의 PATCH는 request가 `REPORTED`이고 `reporterId`가 caller일 때만 가능하다. 다른 status는
`409 INVALID_REQUEST_STATE`이고, 다른 사람의 request는 `403 ACCESS_DENIED`다.

이는 frozen matrix 규칙이다. 결과로 작업이 시작된 뒤에는 resident가 자신의 출입 동의를 바꿀 수
없다. 이미 예약된 visit은 생성 시점에 consent를 snapshot했으므로 나중 변경이 그 visit에 영향을
주지도 않는다. 운영상 동의를 바꿔야 하면 V1에서는 신고를 닫고 다시 올리거나 comment로 전달한다.
이 제약은 frozen에서 왔으므로 이 ADR이 완화하지 않는다.

### Administrator는 active request에서만

`ADMIN`과 `SUPER_ADMIN`의 PATCH는 request가 `REPORTED`, `ASSIGNED`, `IN_PROGRESS`, `ON_HOLD` 중
하나일 때 가능하다. 이것이 matrix가 말하는 "active request"다. `RESOLVED`와 세 terminal 상태는
`409 INVALID_REQUEST_STATE`다.

`RESOLVED`를 제외하는 이유는 작업이 끝난 뒤 분류를 바꾸면 완료된 작업과 기록이 어긋나기 때문이다.
분류를 고쳐야 하면 reopen한 뒤 고친다.

`ADMIN`은 request의 방이 해석되는 dormitory를 scope에 가져야 한다. 아니면 `403 ACCESS_DENIED`다.

### spaceId는 PATCH로 바꾸지 않는다

Matrix의 "location 조정"은 이 슬라이스에서 `facilityId`로만 해석한다. `spaceId`는 PATCH가 받지
않는다.

ADR-019는 `spaceId`를 reporter의 current Residence ROOM으로 묶었고, 그것이 resident 신고 경계의
근거다. PATCH가 `spaceId`를 바꾸게 하면 그 불변식이 깨진다. Administrator가 임의의 방으로 옮길 수
있다면 "resident는 자기 방만 신고한다"는 규칙이 생성 시점에만 성립하는 장식이 된다. 방을 잘못
적은 신고는 reject하고 다시 올리는 것이 V1의 경로다.

`facilityId`는 바꿀 수 있다. 설정, 다른 설비로 교체, `null`로 해제가 모두 가능하다. 새 값은
ADR-019의 생성 규칙과 같은 검사를 받는다. 그 설비는 request의 `spaceId`에 속해야 하고 `RETIRED`일
수 없으며, `OUT_OF_SERVICE`는 허용한다. 위반은 `409 INVALID_REQUEST_STATE`이고 없는 설비는
`404 FACILITY_NOT_FOUND`다.

### categoryId를 바꿔도 priority는 따라가지 않는다

`categoryId`를 바꾸면 category 자체만 바뀐다. `priority`는 새 category의 default로 재설정되지
않는다.

생성 시 priority가 category default에서 온 것은 초기값을 서버가 정해야 했기 때문이다. 그 뒤의
priority는 운영 판단이고, 같은 PATCH에서 함께 보낼 수 있다. 자동으로 덮어쓰면 administrator가
의도적으로 올려둔 우선순위가 분류 수정 한 번에 조용히 내려간다.

새 category는 활성이어야 한다. 비활성 category는 `409 INVALID_REQUEST_STATE`이고 없는 category는
`404 CATEGORY_NOT_FOUND`다.

### 부분 수정의 의미

본문에 있는 field만 바꾼다. 생략한 field는 그대로 둔다.

Nullable field는 `facilityId`, `preferredVisitStart`, `preferredVisitEnd`뿐이고, 이들은 명시적
`null`로 해제할 수 있다. 나머지 field에 `null`을 보내면 `400 VALIDATION_FAILED`다. 생략과 명시적
`null`을 구분해야 하므로 두 DTO는 원시 본문의 key 존재를 보는 wrapper를 쓴다. ADR-023의 visit note가
이 구분을 만들지 않은 것과 다른 이유는, 여기서는 해제가 계약상 필요한 동작이기 때문이다. Resident가
희망 시간을 거둬들이거나 administrator가 잘못 지목한 설비를 떼어내는 일이 실제로 있다.

`preferredVisitStart`와 `preferredVisitEnd`는 수정 후의 최종 상태가 both-or-neither이고, 둘 다
있으면 `start < end`여야 한다. 한쪽만 보내 저장된 다른 쪽과 짝이 맞지 않게 되면
`400 INVALID_VISIT_TIME`이다. 생성 시 규칙을 수정에도 그대로 적용한다.

`title`은 최대 150자, `description`은 최대 2000자다. `description`의 상한은 생성 DTO에 상한이 없는
기존 결함을 이 경로에서 반복하지 않기 위한 것이고, 생성 쪽 수정은 별도 항목으로 남는다.

### Version과 audit

`version`은 필수다. 불일치와 flush 시점의 optimistic-locking 실패는 모두
`409 VERSION_CONFLICT`다. 성공한 PATCH는 `version`을 올리고 `updated_at`을 갱신한다.

변경된 field에 따라 RequestHistory 행을 남긴다. `categoryId`가 바뀌면 `CATEGORY_CHANGED`,
`priority`가 바뀌면 `PRIORITY_CHANGED`, 그 외 metadata가 바뀌면 `REQUEST_METADATA_UPDATED`다. 앞의
둘은 ERD가 예시로 든 이름이고 세 번째는 이 결정으로 추가한다. 각 행은 actor와, 변하지 않은 request
status를 두 status field에 기록하며, `metadata`에 바뀐 field 이름 목록을 담는다. 값 자체는 담지
않는다. 출입 동의와 자유 서술이 audit 행으로 복제되는 것을 막기 위해서다.

한 PATCH가 category와 priority를 모두 바꾸면 두 행을 남긴다. 각 행이 자기 의미를 갖고, ERD가 둘을
별개 event로 이름 붙였기 때문이다. 아무 값도 실제로 바뀌지 않은 PATCH는 history를 남기지 않고
`version`도 올리지 않으며 현재 상태를 반환한다.

V11이 `ck_request_history_event_type`을 교체해 세 event type을 더한다. V6부터 V10까지는 수정하지
않는다.

### Error

`404 REQUEST_NOT_FOUND`, `404 CATEGORY_NOT_FOUND`, `404 FACILITY_NOT_FOUND`,
`403 ACCESS_DENIED`, `409 INVALID_REQUEST_STATE`, `409 VERSION_CONFLICT`,
`400 VALIDATION_FAILED`, `400 INVALID_VISIT_TIME`, `400 INVALID_ENTRY_POLICY`는 모두 기존 frozen
code다. 이 계약은 새 error code를 도입하지 않는다.

인가는 state 검사보다 먼저 평가한다.

## Contract synchronization

승인되면 role별 field matrix, 두 DTO와 role 해석, resident의 `REPORTED` 제한, administrator의
active 상태 집합, `spaceId` 불변과 `facilityId` 변경 규칙, category 변경이 priority를 건드리지 않는
것, 부분 수정과 해제 의미, version과 세 history event type을 Frozen API specification과 permission
matrix에 동기화한다. V11 제약 변경은 ERD note에 기록하고 frozen baseline hash를 갱신한다. 구현은
같은 요청 슬라이스에서 이어진다.

## Alternatives

### 1. role 구분 없이 하나의 DTO를 쓰고 field별로 인가한다

DTO가 하나여서 문서와 client가 단순해진다. 거부한 이유는 frozen 지시가 allow-list된 별도 DTO를
쓰라는 것이고, field별 인가는 "이 field를 보낼 수 있는가"를 런타임 분기로 흩어 놓아 누락 하나가
resident에게 priority를 열어 주기 때문이다. 집합을 타입으로 나누면 그 실수가 역직렬화에서 막힌다.

### 2. `spaceId`를 administrator가 바꿀 수 있게 한다

방을 잘못 적은 신고를 reject 없이 고칠 수 있어 운영이 편하다. 거부한 이유는 ADR-019가 세운
"resident는 자기 current Residence ROOM만 신고한다"는 불변식이 생성 시점에만 유효한 장식으로
바뀌기 때문이다. 신고 대상 방을 바꾸는 것은 metadata 수정이 아니라 다른 신고다.

### 3. `categoryId`를 바꾸면 `priority`를 새 default로 재설정한다

분류와 우선순위가 항상 일관된다. 거부한 이유는 administrator가 의도적으로 올려둔 우선순위가 분류
수정 한 번에 조용히 내려가기 때문이다. 같은 PATCH에서 둘을 함께 보낼 수 있으므로 자동 재설정이
주는 편의는 작다.

### 4. 생략과 명시적 null을 구분하지 않는다

구현이 단순하고 ADR-023의 visit note가 택한 방식과 같다. 거부한 이유는 여기서는 해제가 계약상
필요한 동작이기 때문이다. 희망 시간을 거둬들이거나 잘못 지목한 설비를 떼어내는 경로가 없으면
resident와 administrator 모두 신고를 다시 올리는 수밖에 없다.

### 5. PATCH마다 하나의 history 행만 남긴다

Audit이 단순해진다. 거부한 이유는 ERD가 `CATEGORY_CHANGED`와 `PRIORITY_CHANGED`를 별개 event로
이름 붙였고, 둘을 한 행으로 합치면 그 이름들이 가리키는 사건을 잃기 때문이다.

### 6. history `metadata`에 바뀐 값을 담는다

변경 이력을 history만 읽어서 재구성할 수 있다. 거부한 이유는 그러면 출입 동의와 자유 서술이
append-only 행에 복제되고, resident가 `STAFF_ONLY`를 보지 못하게 한 것과 같은 종류의 노출 경로가
생기기 때문이다. Field 이름 목록만으로 "무엇이 언제 바뀌었는지"는 충분히 추적된다.

### 7. administrator가 `RESOLVED` request의 분류를 고칠 수 있게 한다

통계를 정리하려면 사후 분류 수정이 필요할 수 있다. 거부한 이유는 완료된 작업과 분류가 어긋나고,
matrix가 "active request"로 한정했기 때문이다. reopen 후 수정하는 경로가 이미 있다.

## Consequences

이 슬라이스로 신고 aggregate 자체가 완성된다. 생성, 조회, 배정, 여덟 lifecycle 전이, visit, work
log에 이어 metadata 수정까지 들어오고, 남는 것은 comment와 attachment라는 두 하위 aggregate다.

Role별 DTO 분리는 이 저장소에서 두 번째로 role이 요청 처리 모양을 바꾸는 지점이다. 첫 번째는
ADR-023의 resident visit projection이었다. 그때와 같은 위험이 있다. 나중에 누군가 두 DTO를 합치거나
한쪽에 상대 field를 추가하면 경계가 무너진다. 구현은 두 집합이 교집합을 갖지 않는다는 것과 상대
field가 `400`이 된다는 것을 테스트로 고정해야 한다.

Resident가 `REPORTED` 이후 출입 동의를 바꿀 수 없다는 제약이 남는다. Frozen matrix에서 온 것이므로
이 ADR이 완화하지 않았고, 실무에서 문제가 되면 별도 계약 변경으로 다룬다.

값을 바꾸지 않은 PATCH가 `version`을 올리지 않으므로, client가 "저장했다"는 응답을 받고도 version이
그대로인 경우가 생긴다. 응답이 현재 상태를 담으므로 client는 받은 값을 그대로 쓰면 된다.

comment와 attachment는 범위 밖이다.
