# ADR-027: Attachment 계약

Status: Accepted - V1 authoritative decision

Date: 2026-11-10

2026-11-10 사람 승인으로 이 계약을 제안대로 수락했다. Storage를 port 뒤에 두고 실제 S3 adapter 없이
닫힌 채로 실패하게 하는 것과, request를 읽을 수 있는 resident가 모든 attachment를 보는 것을
포함한다.

## Context

Frozen V1은 Attachment의 흐름과 뼈대를 준다. 흐름은 고정이다. 요청을 인가하고, 서버가 S3 presigned
upload URL을 발급하고, client가 S3로 직접 올리고, client가 별도로 metadata를 등록한다. Download
URL도 resource 인가를 거친다. S3 upload나 network 작업은 request의 core transaction 안에서 실행하지
않는다. ERD는 column을 `id`, `request_id`, `uploader_id`, `type`, unique `object_key`,
`original_filename`, `content_type`, `file_size`, `created_at`으로 정하고 `AttachmentType`을
`REPORT`, `BEFORE_REPAIR`, `AFTER_REPAIR`, `OTHER`로 열거한다. Binary는 S3에 두고 database는
metadata와 object key만 가진다. API inventory는 경로 넷을 둔다. `INVALID_FILE_TYPE`,
`FILE_TOO_LARGE`, `ATTACHMENT_NOT_FOUND`는 이미 frozen error code다. Permission matrix는
`RESIDENT`가 본인 active request에 허용 attachment를 추가하고 `WORKER`가 repair attachment를
작성한다고 적는다.

범위도 이미 정해져 있다. Roadmap은 이 슬라이스가 "attachment metadata, S3 abstraction, presigned
URL contract를 구현한다. 실제 S3 연결은 뒤로 미룬다"고 적는다. ADR-008은 foundation 단계에서 AWS
구현을 추가하지 않는다고 했고, 저장소 규칙은 실제 cloud provisioning을 금지한다.

Frozen 계약이 정하지 않은 것은 다음이다.

1. 네 경로의 DTO
2. object key를 누가 만들고, 등록 시 그 key가 정말 이 caller가 이 request를 위해 받은 것인지 어떻게
   확인하는지
3. 허용 content type과 용량 상한. Frozen은 "limits are not yet product constants"라고 적었다
4. 등록 시점에 실제로 upload가 일어났는지, 선언한 크기와 type이 맞는지 어떻게 검증하는지.
   ADR-008은 PUT header만으로는 안전한 content와 크기를 증명할 수 없다고 경고한다
5. `AttachmentType`별 작성 권한. matrix는 resident의 "허용 attachment", worker의
   "repair attachment"라고만 한다
6. "List allowed metadata"가 누구에게 무엇을 보여주는지
7. 고아 object의 정리 정책. ADR-008과 저장소 규칙이 명시적 정책을 요구한다
8. 실제 S3 연결이 없는 동안 실행 중인 application이 이 경로들에 무엇을 답하는지
9. 삭제 경로. Inventory에 attachment delete가 없다

## Decision

### Storage는 port 뒤에 두고, 연결 전에는 닫힌 채로 실패한다

Application은 `AttachmentStorage` port에만 의존한다. Port는 upload presign, 저장된 object의 실제
크기와 content type 조회, download presign 세 가지를 제공한다.

이 슬라이스는 실제 S3 adapter를 넣지 않는다. Roadmap이 실제 연결을 뒤로 미뤘고, 검증할 S3 없이 AWS
SDK 코드를 넣으면 테스트되지 않은 코드가 된다. 대신 기본 adapter는 모든 호출에 실패하고, 그때
attachment 경로는 `503 STORAGE_UNAVAILABLE`을 답한다. 표준 error 본문 형식은 그대로 쓴다.

닫힌 채로 실패하는 것이 중요하다. "성공한 척하는" 기본 adapter를 두면 object 존재 확인이 항상
통과하고, 누구든 존재하지 않는 파일의 metadata를 등록할 수 있게 된다. 연결되지 않은 storage는
기능이 없는 것으로 정직하게 드러나야 한다.

`503`과 `STORAGE_UNAVAILABLE`은 frozen error 표에 없던 것이고 이 결정으로 추가한다. Frozen의
400/401/403/404/409 의미는 바꾸지 않는다. `503`은 그중 어느 것도 아닌, 의존 인프라가 준비되지 않은
상태를 나타낸다.

통합 테스트는 메모리 fake adapter로 전체 흐름을 검증한다. 실제 S3 adapter는 별도 슬라이스에서
추가하며 port가 그 경계다.

### 흐름과 transaction 경계

Storage 호출은 어떤 database transaction 안에서도 일어나지 않는다. 각 경로는 다음 순서를 따른다.

- Presign: read-only transaction에서 인가와 정책을 확인하고, transaction이 끝난 뒤 storage에
  presign을 요청한다.
- 등록: read-only transaction에서 인가를 확인하고, transaction 밖에서 storage에 object를 조회하고,
  그 결과를 가지고 짧은 write transaction에서 상태를 다시 확인한 뒤 row를 insert한다.
- Download: read-only transaction에서 인가를 확인하고, transaction 밖에서 presign을 요청한다.

### Upload 인가

`POST /maintenance-requests/{id}/attachments/presigned-url`은
`PresignUploadRequest(type, originalFilename, contentType, fileSize)`를 받는다.

응답은 `PresignedUploadResponse(objectKey, uploadUrl, method, headers, expiresAt)`다. Client는
`uploadUrl`로 `headers`를 그대로 붙여 올리고, 등록할 때 `objectKey`를 돌려준다.

Object key는 서버가 만든다. 형식은 `requests/{requestId}/attachments/{uploaderId}/{uuid}`다.
파일명을 key에 넣지 않는다. 파일명은 사용자 입력이고 개인정보일 수 있으며, key에 들어가면 경로
조작과 인코딩 문제의 통로가 된다.

Key에 request와 uploader를 넣는 것이 pending upload table을 대신한다. 등록 시 서버는 제출된 key의
접두가 그 request와 caller 자신을 가리키는지 확인한다. 그래서 다른 사람이 받은 key나 다른 request의
key를 등록할 수 없고, 발급 내역을 database에 저장할 필요가 없다.

Presigned URL은 선언한 content type과 크기에 묶는다. URL의 수명은 설정값이며 기본 10분이다.

### 한도는 product 상수가 아니라 설정이다

Frozen이 "limits are not yet product constants"라고 적었으므로 한도를 code에 박지 않고 설정으로
둔다. 기본값은 다음과 같다.

| 설정 | 기본값 |
|---|---|
| 파일 최대 크기 | 10 MiB |
| 허용 content type | `image/jpeg`, `image/png`, `image/webp`, `application/pdf` |
| request당 최대 attachment 수 | 20 |
| upload URL 수명 | 10분 |
| download URL 수명 | 5분 |

허용되지 않은 content type은 `400 INVALID_FILE_TYPE`, 한도를 넘는 크기나 0 이하의 크기는
`400 FILE_TOO_LARGE`다. Request가 이미 최대 개수의 attachment를 가지면
`409 INVALID_REQUEST_STATE`다.

`originalFilename`은 필수이고 최대 255자이며, 경로 구분자와 제어 문자를 포함할 수 없다. 위반은
`400 VALIDATION_FAILED`다. 저장만 하고 어떤 경로 계산에도 쓰지 않는다.

### 등록은 client의 주장이 아니라 storage가 본 것을 기록한다

`POST /maintenance-requests/{id}/attachments`는
`RegisterAttachmentRequest(objectKey, type, originalFilename)`를 받는다. `contentType`과
`fileSize`는 받지 않는다.

서버는 storage에 그 object를 조회하고 storage가 보고한 크기와 content type을 기록한다. Presign 때
client가 선언한 값은 주장일 뿐이고, 등록 때 다시 받으면 같은 주장을 두 번 믿는 것이다. Storage가
본 값으로 한도를 다시 검사하므로, presign을 통과한 뒤 다른 것을 올린 경우 등록에서
`INVALID_FILE_TYPE`이나 `FILE_TOO_LARGE`로 걸린다.

Object가 storage에 없으면 `409 INVALID_REQUEST_STATE`다. Key의 접두가 그 request와 caller를
가리키지 않으면 `403 ACCESS_DENIED`다. 이미 등록된 key는 `object_key` unique 제약에 걸리며
`409 INVALID_REQUEST_STATE`다.

등록은 request를 수정하지 않는다. status, `updated_at`, `version`을 바꾸지 않고 version을 검사하지
않으며 RequestHistory 행을 남기지 않는다. Attachment row가 기록이다.

### AttachmentType별 작성 권한

권한은 ADR-026과 같이 role이 아니라 그 request에 대한 관계로 정한다.

| 관계 | 쓸 수 있는 type |
|---|---|
| reporter인 `RESIDENT` | `REPORT`, `OTHER` |
| current active assignee인 `WORKER` | `BEFORE_REPAIR`, `AFTER_REPAIR`, `OTHER` |
| scope 안의 `ADMIN`, 그리고 `SUPER_ADMIN` | `OTHER` |

`REPORT`는 신고자가 문제를 보여주는 자료이고 `BEFORE_REPAIR`/`AFTER_REPAIR`는 작업자가 수리를
기록하는 자료다. Administrator는 신고하지도 수리하지도 않으므로 `OTHER`만 쓴다. 관계에 맞지 않는
type은 `403 ACCESS_DENIED`, former assignee는 `403 WORKER_NOT_ASSIGNED`, 가시성 밖 request는
`404 REQUEST_NOT_FOUND`다.

Attachment는 request가 terminal이 아닌 동안 추가할 수 있다. Comment와 같은 경계이며 `RESOLVED`를
포함한다. 수리 후 사진은 resolve 뒤에 올라오는 일이 흔하다. Terminal이면
`409 INVALID_REQUEST_STATE`다. Presign과 등록 양쪽에서 검사한다.

### 목록과 download

`GET /maintenance-requests/{id}/attachments`는 ADR-020의 request read 가시성을 재사용한다. Request를
읽을 수 있는 caller는 그 request의 모든 attachment metadata를 본다. Attachment에는 가시성 column이
없고, 수리 전후 사진은 resident에게 보여줄 이유가 있는 자료다.

응답 항목은 `AttachmentResponse(id, requestId, uploaderId, type, originalFilename, contentType,
fileSize, createdAt)`다. `objectKey`는 포함하지 않는다. Storage 내부 구조이고, download는 항상
인가를 거쳐 발급된 URL로만 한다.

목록은 `createdAt ASC, id ASC`로 정렬하고 페이징하지 않는다. Request당 최대 개수가 설정으로
제한되므로 목록의 크기도 제한된다.

`GET /attachments/{attachmentId}/download-url`은 그 attachment가 속한 request를 caller가 읽을 수
있을 때 `DownloadUrlResponse(url, expiresAt)`를 반환한다. 읽을 수 없으면 없는 attachment와 같은
`404 ATTACHMENT_NOT_FOUND`다. Attachment ID가 순번이므로 `403`은 존재를 드러낸다.

Presigned URL은 어떤 로그에도 남기지 않는다.

### 고아 object 정리 정책

Presign을 받고 upload했지만 등록하지 않은 object가 고아다. 정책은 다음과 같다.

- 등록되지 않은 object는 생성 후 24시간이 지나면 삭제 대상이다. Upload URL의 수명이 10분이고 등록은
  그 직후에 이어지므로 24시간 뒤에도 등록되지 않은 object는 버려진 것이다.
- 삭제는 application이 아니라 storage 쪽 lifecycle 작업이 수행한다. Bucket의 key와 `attachment`
  table의 `object_key`를 대조해 등록되지 않은 것을 지운다.
- Application은 V1에서 어떤 object도 삭제하지 않는다.

Key가 `requests/` 접두 아래에 정해진 형식으로 모이므로 대조 작업이 단순하다. 이 작업 자체의 구현은
실제 S3 연결 슬라이스의 범위다.

### 삭제 경로는 없다

V1은 attachment를 삭제하는 경로를 제공하지 않는다. Inventory에 없고 이 결정도 추가하지 않는다.
등록된 attachment row는 hard-delete하지 않는다. Entity는 모든 column을 `updatable = false`로 매핑한다.

### V13 migration

V13은 ERD가 정한 대로 `attachment`를 만든다. `type`은 CHECK 제약을 가진 string enum,
`object_key`는 `VARCHAR(500)` unique, `file_size`는 양수 CHECK, request와 uploader foreign key는
`ON DELETE RESTRICT`다. 목록을 위해 `(request_id, created_at)` index를 둔다. RequestHistory를 남기지
않으므로 `ck_request_history_event_type`은 건드리지 않는다. V6부터 V12까지는 수정하지 않는다.

## Contract synchronization

승인되면 네 DTO, storage port와 `503 STORAGE_UNAVAILABLE`, 서버 생성 object key와 그 접두 검증,
설정 기반 한도와 기본값, storage가 본 값으로 등록하는 규칙, type별 작성 권한, 추가 가능한 상태,
목록과 download의 가시성, 고아 정리 정책, 삭제 경로 없음을 Frozen API specification과 permission
matrix에 동기화한다. V13 물리 규칙은 ERD note에 기록하고 frozen baseline hash를 갱신한다. 구현은
같은 요청 슬라이스에서 이어진다.

## Alternatives

### 1. 이 슬라이스에서 AWS SDK S3 adapter까지 구현한다

Attachment가 실제로 동작하는 상태로 Phase 3가 끝난다. 거부한 이유는 roadmap이 실제 연결을 뒤로
미뤘고, 저장소 규칙이 cloud provisioning을 금지해 검증할 bucket이 없으며, 검증하지 못한 SDK 코드를
보안 경계에 넣게 되기 때문이다. Port를 먼저 고정하면 adapter는 나중에 계약 변경 없이 들어온다.

### 2. 연결 전에는 성공한 척하는 stub adapter를 기본으로 둔다

Storage 없이도 local에서 흐름을 눌러볼 수 있다. 거부한 이유는 object 존재 확인이 항상 통과하면
존재하지 않는 파일의 metadata를 누구나 등록할 수 있고, 그 adapter가 실수로 배포에 남으면 검증이
통째로 사라지기 때문이다. Fake는 테스트 안에만 둔다.

### 3. 발급한 key를 pending upload table에 저장한다

발급 내역이 database에 남고 수명 만료를 정확히 추적할 수 있다. 거부한 이유는 frozen ERD에 없는
table을 추가하는 일이고, key 접두에 request와 uploader를 넣는 것만으로 "이 key가 이 caller의
것인가"를 상태 없이 확인할 수 있기 때문이다.

### 4. 등록 때 client가 보낸 content type과 크기를 기록한다

Storage 조회가 필요 없어 등록이 database 작업만으로 끝난다. 거부한 이유는 ADR-008이 경고한 그대로
client의 선언이 실제 올린 것과 같다는 보장이 없고, 한도 검사가 주장에 대한 검사로 전락하기
때문이다.

### 5. `objectKey` 대신 불투명 upload token을 준다

Storage 구조가 client에 드러나지 않는다. 거부한 이유는 token과 key의 대응을 저장할 곳이 필요해져
대안 3으로 돌아가기 때문이다. Key는 등록 응답과 목록에는 다시 나오지 않으며, 그것만으로 object에
접근할 수도 없다.

### 6. resident에게는 `REPORT`와 `OTHER` type만 보여준다

수리 사진을 내부 자료로 취급할 수 있다. 거부한 이유는 수리 전후 사진이 resident가 close와 reopen을
판단하는 근거이고, attachment에는 comment의 `STAFF_ONLY`에 해당하는 가시성 개념이 frozen에 없기
때문이다. 내부용 자료가 필요해지면 가시성 column을 추가하는 별도 계약 변경으로 다룬다.

### 7. application이 고아 object를 직접 삭제한다

정리 로직이 저장소 안에 있어 추적하기 쉽다. 거부한 이유는 application에 object 삭제 권한을 주면
최소 권한 원칙에서 멀어지고, 삭제 작업이 요청 처리 경로와 얽히기 때문이다. Storage lifecycle이 같은
일을 더 좁은 권한으로 한다.

## Consequences

이 슬라이스로 Phase 3의 계약 표면이 완성된다. 다만 **실행 중인 application에서 attachment 경로는
실제 S3 adapter가 들어올 때까지 `503 STORAGE_UNAVAILABLE`을 답한다.** 계약, 인가, 검증, metadata
저장은 구현되고 테스트되지만, 파일이 실제로 오가려면 storage 연결 슬라이스가 필요하다.

Storage가 보고한 content type은 여전히 upload 시 client가 붙인 header에서 온다. Presigned URL이 그
header를 묶어 두므로 선언과 다른 type으로 올릴 수는 없지만, 파일의 실제 내용이 그 type인지는
확인하지 않는다. Magic byte 검사나 악성코드 검사는 V1 범위 밖이며, 허용 type을 이미지와 PDF로
좁힌 것이 현재의 방어다.

Object key가 presign 응답으로 client에 한 번 노출된다. Key만으로는 object에 접근할 수 없고 등록
이후의 응답에는 다시 나오지 않는다.

등록된 attachment는 삭제할 수 없다. 잘못 올린 사진을 내릴 방법이 V1에 없으며, 필요해지면 comment의
soft-delete와 같은 방식을 별도 계약 변경으로 추가한다.

Storage 호출을 transaction 밖에 두므로, presign 인가와 등록 사이에 request가 terminal로 바뀔 수
있다. 등록의 write transaction이 상태를 다시 확인해 이를 막는다. 그 사이에 올라간 object는 고아가
되고 정리 정책이 처리한다.
