# ADR-026: Comment 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-10

2026-11-10 사람 승인으로 이 계약을 제안대로 수락했다. Comment를 받는 상태를 terminal이 아닌 모든
상태로 두는 것과, 삭제된 comment를 목록과 개수에서 완전히 제외하는 것을 포함한다.

## Context

Frozen V1은 Comment의 뼈대를 준다. ERD는 column을 `id`, `request_id`, `author_id`, `content`,
`visibility`, `created_at`, nullable `deleted_at`으로 정하고 `CommentVisibility`를 `PUBLIC`,
`STAFF_ONLY`로 열거한다. Comment는 hard-delete하지 않고 `deleted_at`으로 soft-delete한다. API
inventory는 경로 셋을 둔다. `POST /maintenance-requests/{id}/comments`(Add comment),
`GET /maintenance-requests/{id}/comments`(Read permitted comments),
`DELETE /comments/{commentId}`(Soft-delete under reviewed policy)다. Permission matrix는 `RESIDENT`가
본인 active request에 `PUBLIC` comment를 추가하고 `STAFF_ONLY` comment를 보지 못하며, `WORKER`
current active assignee가 `PUBLIC`/`STAFF_ONLY` comment를 작성한다고 적는다. Resident가
`STAFF_ONLY` comment를 절대 받지 않는다는 것은 저장소의 hard invariant다.

Frozen 계약이 정하지 않은 것은 다음이다.

1. create DTO, 그리고 resident가 `STAFF_ONLY`를 보냈을 때의 응답
2. administrator가 comment를 작성하는지. matrix의 `ADMIN` 항목에는 comment가 없지만, ADR-024는
   administrator의 메모가 WorkLog가 아니라 comment에 속한다고 이미 적었다
3. comment를 받는 request 상태. matrix는 resident에 대해 "active request"라고만 한다
4. "Read permitted comments"의 정확한 필터, 그리고 `totalElements`가 무엇을 세는지
5. soft-delete 정책. Frozen이 "under reviewed policy"로 명시적으로 열어두었다. 누가 지울 수 있는지,
   지운 사람을 어디에 기록하는지, 지워진 comment가 목록에 어떻게 보이는지
6. 삭제 대상이 caller에게 보이지 않는 comment일 때의 응답. Resident에게 `STAFF_ONLY` comment의
   존재를 드러내서는 안 된다
7. `content` 상한, 목록 정렬과 페이징
8. comment 수정 경로. Inventory에 comment PATCH가 없다

## Decision

### 작성

`POST /maintenance-requests/{id}/comments`는 `AddCommentRequest(content, visibility)`를 받는다.
`authorId`, `createdAt`, `id`는 받지 않는다. `author_id`는 JWT subject다. `content`는 필수이고 최대
2000자다. `visibility`는 필수이며 기본값을 두지 않는다. 기본값이 있으면 staff가 내부 메모를 쓰려다
생략 하나로 resident에게 공개하는 사고가 생긴다. 해석할 수 없는 값은 `400 VALIDATION_FAILED`다.

작성 권한은 role이 아니라 그 request에 대한 관계로 정한다.

| 관계 | 쓸 수 있는 visibility |
|---|---|
| reporter인 `RESIDENT` | `PUBLIC` |
| current active assignee인 `WORKER` | `PUBLIC`, `STAFF_ONLY` |
| request의 dormitory를 scope에 가진 `ADMIN`, 그리고 `SUPER_ADMIN` | `PUBLIC`, `STAFF_ONLY` |

Administrator가 comment를 작성한다는 것은 matrix의 `ADMIN` 항목에 없던 내용이고 이 결정으로
추가한다. ADR-024가 administrator의 메모를 WorkLog에서 배제하면서 comment로 보냈으므로, 그 창구가
실제로 있어야 한다.

Staff 관계 없이 `STAFF_ONLY`를 쓰려는 caller는 `403 ACCESS_DENIED`다. Reporter이면서 `WORKER`
role도 가진 사람이라도 그 request에 배정되어 있지 않으면 resident로서만 쓸 수 있다. 어떤 관계도
없는 caller는 request가 보이지 않으면 `404 REQUEST_NOT_FOUND`, 보이지만 쓸 수 없으면(former
worker) `403 WORKER_NOT_ASSIGNED`다.

Comment 추가는 request를 수정하지 않는다. status, `updated_at`, `version`을 바꾸지 않고 version을
검사하지 않으며 RequestHistory 행을 남기지 않는다. Comment table 자체가 기록이다.

### Comment를 받는 상태는 terminal이 아닌 모든 상태다

Request가 `CLOSED`, `REJECTED`, `DUPLICATE`가 아니면 comment를 쓸 수 있다. Terminal이면
`409 INVALID_REQUEST_STATE`다.

`RESOLVED`를 포함한다. 작업이 끝나고 resident가 close와 reopen 중 하나를 고르는 시점이야말로 "정말
고쳐졌나요"라는 대화가 필요한 때다.

이는 ADR-025가 administrator의 분류 수정에 대해 정한 상태 집합(`RESOLVED` 제외)보다 넓다. 두
규칙은 목적이 다르다. 분류 수정은 완료된 작업의 기록이 어긋나는 것을 막으려고 `RESOLVED`를
뺐고, comment는 대화이므로 request가 살아 있는 동안 열어둔다. 혼동을 피하기 위해 matrix의 resident
항목에 있는 "active request" 표현을 comment에 대해서는 "terminal이 아닌 request"로 명시해
동기화한다. WorkLog(ADR-024)와 visit outcome(ADR-023)도 같은 "terminal이 아님" 경계를 쓴다.

### 읽기: resident는 PUBLIC만, 그리고 개수도 PUBLIC만 센다

`GET /maintenance-requests/{id}/comments`는 ADR-020의 request read 가시성을 재사용한다. 가시성 밖
request는 `404 REQUEST_NOT_FOUND`다.

Staff 관계를 가진 reader는 `PUBLIC`과 `STAFF_ONLY`를 모두 받는다. Staff 관계는 `SUPER_ADMIN`,
scope 안의 `ADMIN`, 그리고 그 request의 assignment row를 가진 `WORKER`(현재든 종료든)다. Former
worker는 matrix의 historical read-only에 따라 계속 읽는다.

그 외의 reader, 즉 reporter인 `RESIDENT`는 `PUBLIC`만 받는다. 판단 기준은 role 보유가 아니라 그
request에 대한 staff 관계다. `WORKER` role을 가졌어도 그 request와 무관하면 staff view를 얻지
못한다.

필터는 적재된 page를 걸러내는 방식이 아니라 query에서 적용한다. 따라서 resident가 받는
`totalElements`와 `totalPages`는 `PUBLIC` comment만 센다. 개수가 `STAFF_ONLY`를 포함하면 내용은
숨겨도 "내부 메모가 몇 개 있다"는 사실이 샌다.

Resident 경로와 staff 경로는 서로 다른 repository method를 쓴다. 한 method에 flag를 넘기는 방식은
flag 하나를 잘못 넘기는 것으로 hard invariant가 깨진다.

목록은 `createdAt ASC, id ASC`로 정렬한다. Comment는 대화이므로 오래된 것부터 읽는다. 페이징은
`page`가 0부터 시작하고 기본값 `0`, `size`는 `1..100`에 기본값 `20`이다. 위반은
`400 VALIDATION_FAILED`다.

응답 DTO는 `CommentResponse(id, requestId, authorId, content, visibility, createdAt)`다.

### Soft-delete 정책

`DELETE /comments/{commentId}`는 `deleted_at`을 설정하고 `204 No Content`를 반환한다. Row는
지우지 않는다.

지울 수 있는 사람은 둘이다.

- 작성자 본인. 단 그 request에 comment를 쓸 수 있는 관계를 지금도 가지고 있어야 한다. Reporter인
  resident는 항상 그 관계를 가진다. Worker는 current active assignee여야 한다. 저장소의 hard
  invariant가 former worker에게 write 권한을 주지 않으므로, former worker는 자기 comment도 지울 수
  없다.
- Request의 dormitory를 scope에 가진 `ADMIN`과 `SUPER_ADMIN`. 누구의 comment든 지울 수 있다.
  개인정보가 잘못 올라온 경우처럼 운영자가 개입해야 하는 상황을 위한 것이다.

삭제는 request 상태로 막지 않는다. 잘못 올라온 내용은 request가 닫힌 뒤에도 내릴 수 있어야 한다.

삭제된 comment는 모든 reader의 목록에서 빠지고 개수에서도 빠진다. Row와 `content`는 database에
남는다.

ERD에는 `deleted_at`만 있고 누가 지웠는지 적을 column이 없다. Schema를 늘리지 않고 책임을 남기기
위해 삭제는 RequestHistory에 `COMMENT_DELETED` 행을 append한다. Actor와 `{"commentId": <id>}`를
기록하고 `content`는 담지 않는다. 운영자가 다른 사람의 comment를 지운 사실이 audit에 남는다.

### 보이지 않는 comment의 삭제는 404다

Caller가 읽을 수 없는 comment를 지우려 하면 `404 COMMENT_NOT_FOUND`다. 없는 comment, 이미 삭제된
comment, 가시성 밖 request의 comment, 그리고 resident가 지목한 `STAFF_ONLY` comment가 모두 같은
응답을 받는다.

Comment ID는 순번이다. Resident가 `STAFF_ONLY` comment의 ID를 넣었을 때 `403`을 주면 "거기에 내부
메모가 있다"는 것을 알려준다. ADR-020이 read 경로에 세운 원칙과 같다.

Caller가 읽을 수는 있지만 지울 권한이 없으면 `403 ACCESS_DENIED`다. 다른 사람의 `PUBLIC`
comment를 지우려는 resident, 자기 comment를 지우려는 former worker가 여기 해당한다. 이 경우
caller는 이미 그 comment의 존재를 알고 있다.

`COMMENT_NOT_FOUND`는 frozen error 표에 없던 code이고 이 결정으로 추가한다. 표의 404 code들은
"representative"로 열거된 것이며 resource마다 같은 형식의 code를 쓰는 기존 관례를 따른다.

### 수정 경로는 없다

Comment는 작성 후 바꿀 수 없다. Inventory에 수정 경로가 없고 이 결정도 추가하지 않는다. 고치려면
지우고 다시 쓴다. Entity는 `deleted_at`을 제외한 모든 column을 `updatable = false`로 매핑한다.

### V12 migration

V12는 ERD가 정한 대로 `comment`를 만든다. `visibility`는 CHECK 제약을 가진 string enum,
`content`는 NOT NULL `TEXT`, `created_at`은 `NOT NULL DEFAULT CURRENT_TIMESTAMP`, request와 author
foreign key는 `ON DELETE RESTRICT`다. 목록을 위해 `(request_id, created_at)` index를 둔다. V12는
`ck_request_history_event_type`을 교체해 `COMMENT_DELETED`를 더한다. V6부터 V11까지는 수정하지
않는다.

## Contract synchronization

승인되면 create DTO, 관계 기반 작성 권한과 administrator의 작성, comment를 받는 상태, resident의
`PUBLIC` 한정과 개수 규칙, soft-delete 정책과 `COMMENT_DELETED`, 보이지 않는 comment의 `404`,
`COMMENT_NOT_FOUND` code, 수정 경로 없음, 목록 정렬과 페이징을 Frozen API specification과
permission matrix에 동기화한다. V12 물리 규칙은 ERD note에 기록하고 frozen baseline hash를
갱신한다. 구현은 같은 요청 슬라이스에서 이어진다.

## Alternatives

### 1. comment를 받는 상태를 ADR-025와 같은 네 상태로 맞춘다

"active"라는 말이 계약 전체에서 한 가지 뜻만 갖게 되어 일관된다. 거부한 이유는 `RESOLVED`에서
대화를 막으면 resident가 close와 reopen 중 하나를 고르기 직전, 대화가 가장 필요한 시점에 창구가
닫히기 때문이다. 분류 수정과 대화는 목적이 다르므로 경계가 달라도 된다.

### 2. `visibility`에 기본값 `PUBLIC`을 둔다

Resident의 요청이 짧아진다. 거부한 이유는 staff가 내부 메모를 쓰면서 field 하나를 빠뜨리면 그
메모가 resident에게 공개되기 때문이다. 공개 범위는 매번 명시하게 한다.

### 3. 삭제된 comment를 "삭제됨" 자리 표시로 목록에 남긴다

대화의 흐름이 끊기지 않고 무언가 지워졌다는 사실이 보인다. 거부한 이유는 `STAFF_ONLY` comment의
자리 표시를 resident에게 보일 수 없으므로 visibility별로 다른 규칙이 필요해지고, 그 분기가 곧
노출 사고의 자리이기 때문이다. 삭제 사실은 `COMMENT_DELETED` history가 기록한다.

### 4. 삭제를 작성자 본인에게만 허용한다

규칙이 가장 단순하다. 거부한 이유는 다른 사람의 개인정보나 부적절한 내용이 올라왔을 때 운영자가
내릴 방법이 없기 때문이다.

### 5. 지운 사람을 기록하기 위해 `deleted_by_id` column을 추가한다

삭제 정보가 comment row 한 곳에 모인다. 거부한 이유는 frozen ERD의 Comment column을 바꾸는
일이고, 같은 사실을 schema 변경 없이 RequestHistory로 남길 수 있기 때문이다.

### 6. 보이지 않는 comment의 삭제에 `403`을 준다

"없음"과 "권한 없음"을 구분해 client가 다루기 쉽다. 거부한 이유는 comment ID가 순번이어서
resident가 ID를 훑어 `STAFF_ONLY` comment의 존재와 개수를 알아낼 수 있기 때문이다.

### 7. role만으로 staff view를 정한다

판단이 단순해진다. 거부한 이유는 그 request와 무관한 `WORKER`가 다른 request의 내부 메모를 읽게
되고, reporter이면서 `WORKER` role을 가진 사람이 자기 신고에 달린 내부 메모를 읽게 되기 때문이다.

## Consequences

이 슬라이스는 hard invariant인 "resident는 `STAFF_ONLY`를 받지 않는다"가 실제로 걸리는 첫
지점이다. ADR-023의 visit `note`는 field 하나를 가리는 문제였고 여기서는 row 전체와 그 개수를
가리는 문제다. 구현은 resident 경로가 별도 query를 쓴다는 것, `totalElements`가 `PUBLIC`만 센다는
것, 삭제 응답이 `STAFF_ONLY`의 존재를 드러내지 않는다는 것을 각각 테스트로 고정해야 한다.

Comment를 받는 상태가 ADR-025의 분류 수정 상태보다 넓다. 근거는 위에 적었지만, 두 규칙을 함께
읽는 사람에게는 설명이 필요한 차이다.

삭제된 comment의 `content`가 database에 남는다. 개인정보를 이유로 지운 comment도 row는 남으므로,
실제 파기가 필요하면 별도 운영 절차가 필요하다. V1은 이를 다루지 않는다.

`COMMENT_DELETED`를 위해 `ck_request_history_event_type`을 다시 교체한다. Comment 추가는 history를
남기지 않지만 삭제는 남긴다는 비대칭이 있다. 추가는 comment row가 기록이고, 삭제는 지운 사람을
적을 자리가 row에 없기 때문이다.

Attachment는 범위 밖이며 Phase 3의 마지막 슬라이스로 남는다.
