# DormFix 작업 로그

## 2026-11-10 - Attachment 슬라이스 구현, Phase 3 계약 표면 완성

- 브랜치: `feature/maintenance-attachment`를 `feature/maintenance-comment` 위에 스택했다. V13이 V12
  뒤여야 하고 controller와 security 설정을 PR #22와 같은 자리에서 건드리기 때문이다.
- 조사에서 범위 근거를 찾았다. `docs/development/roadmap.md`가 이 슬라이스에 대해 "attachment
  metadata, S3 abstraction, presigned URL contract를 구현한다. 실제 S3 연결은 뒤로 미룬다"고 이미
  적어 두었다. ADR-008의 "foundation 단계에서 AWS 구현을 추가하지 않는다", AGENTS.md의 실제 cloud
  provisioning 금지와 일치한다.
- 사용자 승인으로 `ADR-027`을 Accepted로 전환했다. 승인 시 두 가지를 직접 선택받았다. Storage는
  port 뒤에 두고 실제 S3 adapter 없이 닫힌 채로 실패하게 하며, request를 읽을 수 있는 resident는
  모든 attachment를 본다. Frozen API specification, permission matrix, ERD에 동기화하고 baseline
  hash 3건을 재계산했다. Q11은 resolved다.
- `V13__attachment.sql`: ERD대로 `attachment`를 만들고 `type` CHECK, `object_key` unique,
  `file_size > 0` CHECK, 두 FK ON DELETE RESTRICT, `(request_id, created_at)` index를 두었다.
  RequestHistory를 남기지 않으므로 `ck_request_history_event_type`은 건드리지 않았다.
- Domain: `Attachment`는 모든 column이 `updatable = false`이고 mutator가 없다. `AttachmentType`
  enum을 추가했다.
- Application: `AttachmentStorage` port(upload presign, 저장된 object 조회, download presign),
  `AttachmentPolicy`(설정 기반 한도), `AttachmentObjectKeys`(key 생성과 접두 검증),
  `AttachmentAccess`(database 단계, 각각 짧은 transaction), `AttachmentService`(비-transactional
  orchestration)를 추가했다. `AttachmentService`가 transaction과 storage 호출을 번갈아 수행하므로
  **어떤 storage 호출도 transaction 안에서 일어나지 않는다.**
- Infrastructure: `UnavailableAttachmentStorage`가 기본 adapter이며 모든 호출에 실패한다.
  `AttachmentProperties`가 `dormfix.attachments.*` 설정을 기본값과 함께 바인딩한다. 기본값은 10 MiB,
  jpeg/png/webp/pdf, request당 20개, upload URL 10분, download URL 5분이다. `application.yml`은
  수정하지 않았다.
- API: 경로 넷을 추가하고 `SecurityConfiguration`에 등록했다. 예외 핸들러에
  `ATTACHMENT_NOT_FOUND`, `INVALID_FILE_TYPE`, `FILE_TOO_LARGE`, 그리고 새 status와 code인
  `503 STORAGE_UNAVAILABLE` 매핑을 추가했다. 응답 DTO에는 `objectKey` field가 없다.
- `SecurityBoundaryTest`: maintenance request 경로가 전부 구현되었으므로 "미구현 경로" 단언을 아직
  미구현인 frozen notification 경로로 옮겼다. 단언을 약화시킨 것이 아니라 전제가 바뀐 것을 따라간
  것이다.

### 이 슬라이스의 핵심 설계 판단

- **닫힌 채로 실패한다.** 연결 전에 "성공한 척하는" stub을 기본으로 두지 않았다. Object 존재 확인이
  항상 통과하면 존재하지 않는 파일의 metadata를 누구나 등록할 수 있고, 그 adapter가 배포에 남으면
  검증이 통째로 사라진다. Fake는 `integrationTest` source set에만 있다.
- **Key 접두가 pending upload table을 대신한다.** `requests/{requestId}/attachments/{uploaderId}/{uuid}`
  형식이므로 등록 시 접두만으로 "이 key가 이 caller가 이 request를 위해 받은 것인가"를 상태 없이
  확인한다. `AttachmentObjectKeysTest`가 다른 request, 다른 uploader, id가 접두만 겹치는 경우
  (1 대 12), traversal, 추가 segment, 대문자 UUID, 개행을 모두 거부함을 확인한다.
- **등록은 storage가 본 값을 기록한다.** 등록 DTO는 content type과 크기를 받지 않는다. 통합 테스트가
  "1000 byte PNG로 presign하고 2048 byte JPEG를 올린" 경우 기록이 JPEG/2048이 됨을, 그리고 허용 밖
  type이나 한도 초과를 올리면 presign을 통과했어도 등록에서 `INVALID_FILE_TYPE`/`FILE_TOO_LARGE`로
  걸림을 확인한다.
- **인가와 검증이 storage보다 먼저다.** `AttachmentStorageUnavailableIntegrationTest`가 fake 없이
  실제 출하 상태를 검증한다. 인가된 presign은 표준 error 본문의 `503`을, 가시성 밖 caller는
  `404`를, 허용 밖 type은 `400`을, 위조 key는 `403`을 storage에 닿기 전에 받는다. 목록은 storage를
  쓰지 않으므로 `200`이다.

### 검증 결과

- 요청 순서대로 전부 통과했다: `gradlew --stop`(No Gradle daemons are running) →
  `python scripts/check_repository.py` → `git diff --check`(기존 LF/CRLF 경고만) →
  `check bootJar --no-daemon --console=plain` → `integrationTest --no-daemon --console=plain`.
- 최종 integrationTest 집계: **24 suites, 166 tests, 0 failures, 0 errors, 0 skipped.** Attachment
  신규 기여는 19(본체 15, storage 미연결 4)다. 통합 테스트 첫 실행에서 실패가 없었다.
- 캐시나 빌드 산출물을 삭제하지 않았다. 위 게이트는 `caabf24`(comment 브랜치 tip) 위의 작업 트리에서
  실행했다. 이후 PR #22가 merge commit `afaaedb`로 병합되어 브랜치를 그 위로 fast-forward했는데,
  `git diff caabf24 afaaedb`가 비어 있으므로 검증한 tree와 같다.

### 남은 작업과 주의사항

- PR #22(comment)가 병합된 뒤 사용자 요청으로 이 슬라이스를 커밋하고 push해 `main` 대상 PR을
  올렸다. PR 번호와 병합 여부는 GitHub에서 확인한다.
- **실행 중인 application에서 attachment의 presign, 등록, download-url은 `503`이다.** 실제 S3
  adapter(`S3Presigner`, `HeadObject`)와 고아 object 정리 lifecycle 작업은 별도 슬라이스다. Port가
  그 경계이므로 adapter는 계약 변경 없이 들어온다.
- Storage가 보고한 content type은 upload 시 client가 붙인 header에서 온다. Magic byte 검사와
  악성코드 검사는 V1 범위 밖이며 ADR-027 Consequences에 기록했다.
- Request당 개수 한도는 등록 transaction에서 다시 확인하지만 동시 등록 둘이 한도를 한 개 넘길 수
  있다. 한도는 보호 장치이지 불변식이 아니므로 잠금을 두지 않았다.
- 등록된 attachment를 삭제하는 경로가 없다. 잘못 올린 파일을 내릴 방법이 V1에 없다.
- `PROJECT_CONTEXT.md`의 "현재 상태"를 실제에 맞게 다시 썼고 Phase 3 결정 요약 절을 추가했다.
  이전 내용은 브랜치와 구현 상태가 실제와 달랐다.
- Phase 3 이후 남은 frozen 범위: notification 경로와 domain event side effect, 실제 S3 adapter,
  배포. 다음 제품 기능은 사용자 요청 없이는 구현하지 않는다.
- 다음 예정 작업은 사용자 결정대로 **전체 코드리뷰**다. 인증 슬라이스부터 하나씩 진행한다. 리뷰 때
  함께 다룰 항목: 신고 생성 슬라이스 deferred 지적 1·2·3·5·6·8번, 생성 DTO와 PATCH의
  `description` 상한 불일치, `RequestRelationshipResolver`로 관계 판단 모으기, Visit 목록의 페이징
  부재.
- 이 슬라이스 외에 열린 PR: #9(setup-java, dependabot 리베이스 대기), #16(Gradle 9.8.2 전용 작업).

## 2026-11-10 - Comment 슬라이스 구현

- 브랜치: `feature/maintenance-comment`를 `feature/maintenance-request-patch` 위에 스택했다. V12가
  V11 뒤여야 하고 `MaintenanceRequestController`/`SecurityConfiguration`을 PR #21과 같은 자리에서
  건드리기 때문이다. PR #21이 머지되면 `main`으로 리베이스된다.
- 사용자 승인으로 `ADR-026`을 Accepted로 전환했다. 승인 시 두 가지를 직접 선택받았다. Comment를
  받는 상태는 terminal이 아닌 모든 상태(`RESOLVED` 포함)이고, 삭제된 comment는 목록과 개수에서
  완전히 제외한다. Frozen API specification, permission matrix, ERD에 동기화하고 baseline hash
  3건을 재계산했다. Frozen error 표의 404 code에 `COMMENT_NOT_FOUND`를 추가했다. Q10은 resolved다.
- `V12__comment.sql`: ERD대로 `comment`를 만들고 `visibility` CHECK, `content` NOT NULL TEXT,
  두 FK ON DELETE RESTRICT, `deleted_at >= created_at` CHECK, `(request_id, created_at)` index를
  두었다. `ck_request_history_event_type`을 교체해 `COMMENT_DELETED`를 더했다. V6~V11은 수정하지
  않았다.
- Domain: `Comment`는 `deletedAt`을 제외한 모든 column을 `updatable = false`로 매핑하고 mutator는
  `softDelete` 하나뿐이다. 수정 경로가 없다. `CommentVisibility` enum을 추가했다.
- Application: `CommentService`가 add, list, delete를 제공한다.
  `RequestRelationshipResolver`를 새로 두어 caller가 그 request에 대해 무엇인지(reporter,
  administrator, current assignee, ever assigned)를 한 곳에서 해석한다. 판단 기준은 role 보유가
  아니라 관계다. 무관한 `WORKER`나, reporter이면서 `WORKER` role을 가졌지만 배정되지 않은 사람은
  staff view를 얻지 못한다.
- API: 경로 셋(POST/GET comments, DELETE /comments/{id})을 추가하고 `SecurityConfiguration`에
  등록했다. 예외 핸들러에 `COMMENT_NOT_FOUND` 매핑을 추가했다. `visibility`에는 기본값이 없다.

### hard invariant가 샐 수 있는 세 지점을 각각 막았다

"resident는 `STAFF_ONLY`를 받지 않는다"가 실제로 걸리는 첫 슬라이스다. Visit의 `note`는 field
하나를 가리는 문제였고 여기는 세 군데다.

- **row**: resident 경로와 staff 경로가 `CommentRepository`의 별도 method를 쓴다
  (`findPublicPage`/`findStaffPage`). 어느 method도 visibility나 boolean flag를 인자로 받지 않으며
  adapter가 `PUBLIC`을 고정한다. `CommentRepositoryContractTest`가 port에 visibility/boolean 인자를
  받는 method가 하나도 없음을 reflection으로 단언한다.
- **개수**: `countPublic`/`countStaff`도 분리했다. resident가 받는 `totalElements`와 `totalPages`는
  `PUBLIC`만 센다. 통합 테스트가 `PUBLIC` 1개 + `STAFF_ONLY` 3개 상황에서 resident total이 1,
  staff total이 4임을 확인한다.
- **삭제 응답**: caller가 읽을 수 없는 comment의 삭제는 없는 comment와 똑같이
  `404 COMMENT_NOT_FOUND`다. 통합 테스트가 resident의 `STAFF_ONLY` 삭제 시도와 존재하지 않는 id의
  삭제 시도가 같은 status를 받고, 본문에 `ACCESS_DENIED`나 내용이 없음을 확인한다.

### 그 밖에 테스트로 고정한 것

- 삭제는 `COMMENT_DELETED` history에 actor와 `commentId`만 남기고 content는 담지 않는다. 테스트가
  metadata에 본문 조각이 없음을 확인한다.
- former assignee는 계속 읽지만 쓰지 못하고(`403 WORKER_NOT_ASSIGNED`) 자기 comment도 지우지
  못한다(`403`). hard invariant "former worker has no write authority"의 적용이다.
- comment 추가는 request의 version과 RequestHistory 개수를 바꾸지 않는다.
- `RESOLVED`에서는 comment가 되고 `CLOSED`에서는 `409`다. 삭제는 닫힌 request에서도 된다.

### 검증 결과

- 요청 순서대로 전부 통과했다: `gradlew --stop`(No Gradle daemons are running) →
  `python scripts/check_repository.py` → `git diff --check`(기존 LF/CRLF 경고만) →
  `check bootJar --no-daemon --console=plain` → `integrationTest --no-daemon --console=plain`.
- 최종 integrationTest 집계: **22 suites, 147 tests, 0 failures, 0 errors, 0 skipped.** Comment
  신규 기여는 16이다. 통합 테스트 첫 실행에서 실패가 없었다. 실행 로그의 Hikari
  "Failed to validate connection" WARN은 종료된 테스트 컨테이너에 대한 pool housekeeper 로그이며
  테스트 실패가 아니다.
- 캐시나 빌드 산출물을 삭제하지 않았고 commit, push, reset, checkout을 수행하지 않았다.

### 남은 작업과 주의사항

- 커밋되지 않은 변경이 `feature/maintenance-comment`에 남아 있다. PR #21(metadata PATCH)이 머지된
  뒤 `main`으로 리베이스해 PR을 올리는 것이 깔끔하다.
- Phase 3 남은 범위는 attachment 하나다. S3 presigned(ADR-008/010), 업로드 후 metadata 등록,
  용량·타입 검증(`INVALID_FILE_TYPE`, `FILE_TOO_LARGE`), 고아 객체 정리 정책까지 얽혀 가장 크다.
  `SecurityBoundaryTest`의 "미구현 경로" 목록에는 이제 attachments만 남아 있다.
- `RequestRelationshipResolver`는 이번에 새로 만들어 `CommentService`만 쓴다.
  `MaintenanceVisitService`, `WorkLogService`, `MaintenanceRequestQueryService`,
  `MaintenanceRequestLifecycleService`는 여전히 같은 종류의 관계 판단을 각자 inline으로 한다.
  AGENTS.md의 "공통 규칙을 feature마다 복제하지 않는다"에 비추면 정리 대상이며, 전체 코드리뷰 때
  이 resolver로 모으는 것을 검토한다.
- 삭제된 comment의 `content`는 database에 남는다. 개인정보를 이유로 지운 경우에도 row는 남으므로
  실제 파기가 필요하면 별도 운영 절차가 필요하다. V1은 이를 다루지 않으며 ADR-026 Consequences에
  기록했다.
- 열린 PR: #21(metadata PATCH), #9(setup-java, dependabot 리베이스 대기), #16(Gradle 9.8.2 전용
  작업).
- 전체 코드리뷰는 Phase 3 완료 후 인증 슬라이스부터 하나씩 수행한다.

## 2026-11-10 - MaintenanceRequest metadata PATCH 슬라이스 구현

- 브랜치: `feature/maintenance-request-patch`를 `feature/maintenance-work-log` 위에 스택했다. PATCH가
  `MaintenanceRequestController`와 `SecurityConfiguration`을 PR #20과 같은 자리에서 건드리므로
  충돌을 피하기 위해서다. PR #20이 머지되면 `main`으로 리베이스된다.
- 사용자 승인으로 `ADR-025`를 Accepted로 전환했다. `spaceId`를 PATCH로 바꾸지 않는 것, 두 role을
  겸한 caller를 administrator로 해석하는 것, 값이 바뀌지 않은 PATCH가 `version`을 올리지 않는 것이
  제안대로 확정되었다. Frozen API specification, permission matrix(field matrix 표), ERD에
  동기화하고 baseline hash 3건을 재계산했다. Q09는 resolved다. Frozen API specification이
  "Exact patch field matrices are a review item"으로 지목했던 공백이 이 ADR로 닫혔다.
- `V11__maintenance_request_metadata_patch.sql`: `ck_request_history_event_type`을 교체해
  `CATEGORY_CHANGED`, `PRIORITY_CHANGED`, `REQUEST_METADATA_UPDATED`를 더했다. V6~V10은 수정하지
  않았다. 테이블 변경은 없다.
- Domain: `MaintenanceRequest.reviseReport`(resident field, `REPORTED`에서만)와
  `reclassify`(administrator field, active 상태에서만)를 추가했다. 둘 다 모든 field의 최종값을
  받아 실제로 바뀐 field 이름 목록을 안정된 순서로 반환하고, 바뀐 것이 없으면 `updatedAt`도
  건드리지 않는다. 어느 command도 `spaceId`를 파라미터로 받지 않는다. `isActive()`를 추가했다.
- Application: `MaintenanceRequestPatchService`가 `patchAsResident`와 `patchAsAdministrator`를
  제공한다. 인가 → version → state → reference 검증 순서를 지킨다. category와 facility는 값이 실제로
  바뀔 때만 검증해서, 같은 값을 다시 보낸 no-op이 비활성 category 때문에 409가 되지 않는다.
  history `metadata`에는 `{"fields":[...]}`로 이름만 담는다. `PatchField`가 생략과 명시적 null을
  구분한다.
- API: `MetadataPatchParser`가 원시 JSON tree를 role별 닫힌 allow-list로 해석한다. Record에
  binding하지 않는 이유는 PATCH가 생략과 명시적 null을 구분해야 하기 때문이다. 상대 집합의 field나
  metadata가 아닌 field는 unknown으로 `400`이다. `SecurityConfiguration`에 PATCH 경로를 등록했다.
- 상세 결과 매핑을 `MaintenanceRequestDetailResult.of(...)`로 옮겨 `MaintenanceRequestQueryService`와
  PATCH 응답이 공유하게 했다. 매핑 복제를 피하기 위한 것이다.
- `SecurityBoundaryTest`: PATCH가 구현되었으므로 "미구현 경로" 목록에서 빼고(이제 comments와
  attachments만 남는다) "구현된 경로는 deny-all에 막히지 않는다" 테스트로 옮겼다. 단언을 약화시킨
  것이 아니라 전제가 바뀐 것을 따라간 것이다.

### 테스트로 고정한 것

- `MetadataPatchParserTest`: 두 field 집합의 교집합이 `version`뿐임을, `status`/`spaceId`/
  `reporterId` 등 금지 field가 어느 집합에도 없음을 직접 단언한다. 나중에 누군가 한쪽 집합에 상대
  field를 추가하면 빌드가 깨진다. ADR-025 Consequences가 지목한 위험에 대한 방어다.
- `MaintenanceRequestPatchTest`: 바뀐 field 이름만 반환, 동일 값이면 아무것도 건드리지 않음,
  category 변경이 priority를 재설정하지 않음, 희망 시간의 pair 규칙, 상태 제한, setter 부재.
- `MaintenanceRequestPatchIntegrationTest` 13개: resident가 priority/category/facility/status/space에
  닿지 못함, administrator가 entry consent와 신고 본문에 닿지 못함, history metadata에 값이 없고
  이름만 있음, category와 priority가 별개 행으로 audit됨, facility 설정·해제·space 검증·RETIRED 거부·
  OUT_OF_SERVICE 허용, scope와 active 상태 제한, 희망 시간 설정·해제·pair, stale version, 값이 바뀌지
  않은 PATCH의 version과 history 불변, WORKER 403, dual-role caller의 administrator 해석.

### 검증 결과

- 요청 순서대로 전부 통과했다: `gradlew --stop`(No Gradle daemons are running) →
  `python scripts/check_repository.py` → `git diff --check`(기존 LF/CRLF 경고만) →
  `check bootJar --no-daemon --console=plain` → `integrationTest --no-daemon --console=plain`.
- 최종 integrationTest 집계: **21 suites, 131 tests, 0 failures, 0 errors, 0 skipped.** PATCH 신규
  기여는 13이다. 첫 `integrationTest` 시도는 Docker Desktop이 꺼져 있어 `initializationError`였고,
  Docker를 기동한 뒤 재실행했다. 코드 문제로 인한 실패는 없었다.
- 캐시나 빌드 산출물을 삭제하지 않았고 commit, push, reset, checkout을 수행하지 않았다.

### 남은 작업과 주의사항

- 커밋되지 않은 변경이 `feature/maintenance-request-patch`에 남아 있다. PR #20(WorkLog)이 CI 4/4
  녹색으로 머지 대기 중이며, 머지 후 이 브랜치를 `main`으로 리베이스해 PR을 올리는 것이 깔끔하다.
- 이 슬라이스로 신고 aggregate 자체가 완성되었다. Phase 3 남은 범위는 comment와 attachment 두 하위
  aggregate다. Comment는 `CommentVisibility`와 soft-delete 정책을, attachment는 S3 presigned
  (ADR-008/010)를 함께 결정해야 한다.
- Frozen에서 온 제약: resident는 `REPORTED` 이후 출입 동의를 바꿀 수 없다. ADR-025가 완화하지
  않았으며 실무에서 문제가 되면 별도 계약 변경 사안이다.
- 생성 DTO의 `description`에는 여전히 상한이 없다(deferred 지적 3번). PATCH 경로는 2000자 상한을
  두었으므로 두 경로가 불일치한다. 전체 코드리뷰 때 생성 쪽을 맞춘다.
- RequestHistory event type을 늘리는 슬라이스는 `ck_request_history_event_type`을 또 교체해야 한다.
  V7, V8, V9, V11이 선례다.
- 열린 PR: #20(WorkLog), #9(setup-java, dependabot 리베이스 대기), #16(Gradle 9.8.2 전용 작업).
- 전체 코드리뷰는 Phase 3 완료 후 인증 슬라이스부터 하나씩 수행한다.

## 2026-11-08 - WorkLog 슬라이스 구현

- 브랜치: `feature/maintenance-work-log`를 `feature/maintenance-visit` 위에 스택했다. V10의
  `visit_id` FK가 V9의 `maintenance_visit`을 필요로 하므로 `main`에서는 뜰 수 없다. PR #19가
  머지되면 `main`으로 리베이스된다.
- 조사 중 정정: `WorkActionType`이 정의되지 않았을 것으로 의심했으나 **Frozen ERD의 enum 표에 이미
  있었다** (`INSPECTION`, `REPAIR`, `REPLACEMENT`, `TEST`, `NOTE`). 결정 범위가 그만큼 좁았다.
- 사용자 승인으로 `ADR-024`를 Accepted로 전환했다. 읽기를 staff 전용으로 두는 것과 `ADMIN`이
  작성하지 않는 것이 제안대로 확정되었다. Frozen API specification, permission matrix, ERD(V10 물리
  규칙)에 동기화하고 baseline hash 3건을 재계산했다. Q08은 resolved다.
- `V10__work_log.sql`: ERD대로 `work_log`를 만들고 request/visit/worker FK 모두 ON DELETE RESTRICT,
  `action_type` CHECK, `description` NOT NULL TEXT를 두었다. 목록용 `(request_id, created_at)`과
  `(visit_id)` index를 추가했다. **`ck_request_history_event_type`은 교체하지 않았다** — WorkLog
  추가가 RequestHistory를 남기지 않기 때문이다. V7/V8/V9가 연속으로 교체해 온 제약이 이번에는
  그대로다.
- Domain: `WorkLog`는 모든 column을 `updatable = false`로 매핑하고 mutator를 두지 않아 append-only를
  코드에서도 고정한다. `WorkActionType` enum을 추가했다.
- Application: `WorkLogService`가 append와 목록을 제공한다. 인가를 state·reference 검증보다 먼저
  수행한다. 작성은 current active assignee만 하고 `ADMIN`은 `403 ACCESS_DENIED`다. 읽기는 staff
  전용이며 `RESIDENT`는 `403`이다. `visitId`가 같은 request의 visit인지 application에서 검증한다 —
  제약이 두 행에 걸쳐 있어 FK로는 표현할 수 없다.
- API: 경로 둘을 추가하고 `SecurityConfiguration`에 POST/GET을 각각 등록했다.
  `AppendWorkLogRequest.description`에 `@Size(max = 2000)`을 두었다. ERD가 `TEXT`만 말하지만 DTO
  상한이 없으면 무제한 저장되므로 AGENTS.md의 신규 코드 규칙을 따랐다. 새 error code는 없다.

### ADR-024에서 기록한 설계 판단

- WorkLog 추가는 request를 전혀 수정하지 않는다. status, `updated_at`, `version`을 바꾸지 않고
  version을 검사하지도 않는다. 새 행만 추가하므로 분실 갱신이 있을 수 없다. 테스트가 request status,
  version, RequestHistory 개수가 모두 불변임을 확인한다.
- 목록은 `createdAt ASC, id ASC`다. WorkLog는 작업의 서술이라 오래된 것부터 읽어야 이야기가 된다.
  Visit 목록이 `scheduledStart DESC`인 것과 의도적으로 다르다.
- WorkLog 목록은 페이징하고 Visit 목록은 하지 않는다. Visit은 열린 visit 하나 규칙이 개수를 제한하는데
  WorkLog에는 그런 제한이 없다. 다만 취소·완료된 visit도 누적될 수 있으므로 Visit 쪽 논리가 완전하지
  않다는 점을 ADR-024 대안 6번에 약점으로 적었다.
- terminal request 거부 규칙은 실무상 도달 불가다. terminal 전이가 assignment를 종료하므로 작성
  권한이 먼저 `403`으로 끊긴다. ADR-022가 같은 종류의 공문구 규칙을 세 전이 모두에 유지한 것과 같은
  이유로 명시하고 강제했다. 테스트는 닫힌 request에서 `403`이 나오고 행이 생기지 않음을 확인한다.

### 검증 결과

- 요청 순서대로 전부 통과했다: `gradlew --stop`(No Gradle daemons are running) →
  `python scripts/check_repository.py` → `git diff --check`(기존 LF/CRLF 경고만) →
  `check bootJar --no-daemon --console=plain` → `integrationTest --no-daemon --console=plain`.
- 최종 integrationTest 집계: **20 suites, 118 tests, 0 failures, 0 errors, 0 skipped.** WorkLog
  신규 기여는 15다. 이번 슬라이스는 통합 테스트 첫 실행에서 실패가 없었다.
- 캐시나 빌드 산출물을 삭제하지 않았고 commit, push, reset, checkout을 수행하지 않았다.

### 남은 작업과 주의사항

- 커밋되지 않은 변경이 `feature/maintenance-work-log`에 남아 있다. 커밋·push·PR은 요청 시에만 한다.
  PR은 #19가 머지된 뒤 `main`을 대상으로 올리는 것이 깔끔하다.
- Phase 3 남은 범위: comment, attachment, 신고 metadata PATCH. Comment는 `CommentVisibility`와
  soft-delete 정책을 함께 결정해야 하고, attachment는 S3 presigned(ADR-008/010)가 얽혀 가장 크다.
- 열린 PR: #19(Visit + ADR 번역), #9(setup-java, dependabot 리베이스 대기),
  #16(Gradle 9.8.2, `check_repository.py:31-32` 해시 2건 갱신이 필요한 전용 작업).
- 전체 코드리뷰는 Phase 3 완료 후 인증 슬라이스부터 하나씩 수행한다. 생성 슬라이스 deferred 지적
  1·2·3·5·6·8번이 그대로 남아 있다.
- 사용자 지시: 앞으로 ADR과 PR 본문은 한국어로 쓴다. Frozen 계약 문서(`api-spec-v1.md`,
  `erd-v1.md`)와 커밋 메시지는 영어를 유지한다.

## 2026-11-07 - Visit 슬라이스 구현

- 사용자 승인으로 ADR-023을 Accepted로 전환했다. `complete`의 actual timestamp를 client가 제공하는
  것과 `RESIDENT`가 목록에서 `note`를 받지 않는 것이 제안대로 확정되었다. Frozen API specification,
  permission matrix, ERD(V9 물리 규칙)에 동기화하고 baseline hash 3건을 재계산했다. Q07은 resolved다.
- `V9__maintenance_visit.sql`: ERD대로 `maintenance_visit`을 만들고 세 user FK와 request FK 모두
  ON DELETE RESTRICT, status/entry_policy_snapshot CHECK, `scheduled_start < scheduled_end` CHECK,
  actual 쌍의 순서 CHECK, 그리고 actual timestamp가 `COMPLETED`일 때만 존재하도록 하는 CHECK를
  두었다. 마지막 제약이 `NO_ACCESS`/`CANCELED`가 소요 시간을 주장하지 못하게 막는다. 열린 visit
  하나를 보장하는 partial unique index `ux_visit_open_request`와 목록용
  `(request_id, scheduled_start DESC)`, worker용 index를 추가했다. V6/V7/V8은 수정하지 않고
  `ck_request_history_event_type`만 교체해 16개 event type을 허용한다.
- Domain: `MaintenanceVisit`은 생성 시 consent snapshot을 받고 그 두 필드를 `updatable = false`로
  두어 코드에서도 immutable하게 고정한다. `reschedule`은 구간이 실제로 움직였는지를 반환해 호출자가
  audit 행을 남길지 결정하게 한다. `complete`/`recordNoAccess`/`cancel`은 모두 `SCHEDULED`에서만
  동작하고 결과는 terminal이다. `VisitStatus` enum을 추가했다.
- Application: `MaintenanceVisitService`가 scheduling, patch, 세 outcome command, 목록을 제공한다.
  `requireManageAuthority`는 인가만 하고, 새 visit의 worker 해석은 `resolveVisitWorker`로 분리해
  scheduling만 active assignment를 요구한다. 목록 가시성은 request read 가시성을 재사용한다.
- API: 경로 6개를 추가하고 `SecurityConfiguration`에 등록했다. `anyRequest().denyAll()`이므로
  GET/PATCH/POST를 각각 명시해야 한다. 예외 핸들러에 `VISIT_NOT_FOUND`와
  `400 INVALID_VISIT_TIME` 매핑을 추가했다.
- **역할별 projection을 구조적으로 안전하게 만들었다.** ADR-023이 "나중에 누군가 staff DTO를
  재사용해 resident 경로가 `note`를 얻으면 안 된다"고 적은 지점이다. `VisitResponse`에 범용
  `from()`을 두지 않고 `forStaff`와 `forResident`만 두어 호출자가 반드시 선택하게 했고,
  `@JsonInclude(NON_NULL)`로 resident 응답에서는 `note` 키 자체가 사라진다. `VisitProjectionTest`가
  직렬화 결과에 `note` 키가 없음을 확인하고, 범용 `from()`이 존재하지 않는다는 것까지 reflection으로
  검증한다.
- PATCH의 note 의미는 단순화했다. 제공된 note가 교체하고 생략하면 유지한다. 처음에는 "생략 vs 명시적
  null"을 구분하려고 servlet filter를 참조했는데, ADR에 없는 의미를 발명한 것이고 존재하지 않는
  클래스였으므로 되돌렸다. V1에는 note를 지우는 경로가 없고 계약도 요구하지 않는다.

### 통합 테스트가 잡은 2건

- 첫 실행은 20 tests / 3 failures였다.
- (1) 2건은 `HttpComponentsClientHttpRequestFactory`가 test classpath에 없어
  `NoClassDefFoundError`였다. 새 의존성을 추가하지 않고 Spring 내장 `JdkClientHttpRequestFactory`로
  교체했다. 기본 simple factory는 PATCH를 보내지 못한다.
- (2) 1건은 **실제 구현 버그**였다. 닫힌 request의 `cancel`이 200이 아니라 409를 반환했다.
  `requireManageAuthority`가 인가와 "새 visit의 worker 해석"을 한 메서드에서 처리하면서 active
  assignment를 항상 요구했는데, ADR-022에 따라 close가 assignment를 종료하므로 닫힌 request에는
  active assignment가 없다. 그래서 ADR-023이 "`cancel`은 항상 가능해 닫힌 request에 남은 열린
  visit을 정리한다"고 정한 경로가 막혔다. 두 책임을 분리해 scheduling만 assignment를 요구하도록
  고쳤다. 계약, 마이그레이션, 에러 매핑은 바꾸지 않았다.

### 검증 결과

- 요청 순서대로 전부 통과했다: `gradlew --stop`(No Gradle daemons are running) →
  `python scripts/check_repository.py` → `git diff --check`(기존 LF/CRLF 경고만) →
  `check bootJar --no-daemon --console=plain` → `integrationTest --no-daemon --console=plain`.
- 최종 integrationTest 집계: **19 suites, 103 tests, 0 failures, 0 errors, 0 skipped.** Visit 신규
  기여는 20이다. 단위 `check`는 Checkstyle 3종과 ArchUnit 포함 통과했다.
- 캐시나 빌드 산출물을 삭제하지 않았고 commit, push, reset, checkout을 수행하지 않았다.

### 남은 작업과 주의사항

- 커밋되지 않은 변경이 `feature/maintenance-visit`에 남아 있다. 커밋·push·PR은 요청 시에만 한다.
- Phase 3 남은 범위: work log, attachment, comment, 신고 metadata PATCH. `WorkLog.visit_id`는 이제
  가리킬 테이블이 있다. Attachment는 S3 presigned(ADR-008/010)가 얽혀 가장 무겁다.
- Visit의 `worker_id`는 scheduling 시점의 active assignee다. 이후 재배정되면 `worker_id`가 former
  assignee인 visit이 남고 command 권한은 새 assignee로 간다. 의도된 이력이며 ADR-023에 기록했다.
- RequestHistory event type을 늘리는 슬라이스는 `ck_request_history_event_type`을 또 교체해야 한다.
  V7, V8, V9가 선례다.
- 열린 PR: #9(setup-java, dependabot 리베이스 대기), #16(Gradle 9.8.2, `check_repository.py`의
  wrapper 해시 2건 갱신이 필요한 전용 작업). #11과 #17은 근거를 코멘트로 남기고 닫았다.
- 전체 코드리뷰는 Phase 3 완료 후 인증 슬라이스부터 하나씩 수행한다. 생성 슬라이스 deferred 지적
  1·2·3·5·6·8번이 그대로 남아 있다.

## 2026-11-06 - ADR 전수 한국어화, Visit 계약 제안

- 베이스 정리: PR #18 병합 후 로컬 `main`을 `origin/main`으로 fast-forward하고
  `feature/maintenance-visit`를 생성했다. PR #2/#3/#4/#8/#13도 병합되어 Actions 버전과
  ArchUnit 1.5.1이 반영된 상태다.
- Proposed `ADR-023`(MaintenanceVisit 계약)을 작성하고 `open-questions.md`에 Q07로 등록했다.
  Frozen이 테이블 컬럼, `VisitStatus` 4개, 생성 가능한 request 상태, consent snapshot immutability,
  visit 완료가 request resolve가 아니라는 점, 여섯 경로, `INVALID_VISIT_TIME`/`VISIT_NOT_FOUND`를
  이미 정해두었고, ERD는 history event 예시가 완전한 enum이 아니라고 명시해 두었다. ADR-023은
  `worker_id`의 서버 유도, request당 열린 visit 하나와 partial unique index, PATCH 허용 필드,
  actual timestamp를 client가 제공하는 이유, terminal request에서 `cancel`만 허용, 과거 시각 허용,
  RESIDENT의 `note` 제외, version 미검사, 5개 event type과 V9를 제안한다.
- **사용자 결정: ADR 문서를 전수 한국어화한다.** Frozen 계약 문서 `api-spec-v1.md`와 `erd-v1.md`는
  영어를 유지한다. 두 파일은 DTO 시그니처·endpoint·error code 나열이 대부분이고 baseline hash
  churn을 피하기 위해서다.
- ADR-001~023 23개를 한국어로 다시 썼다. 한글 비율 22~41%로, 한국어 중심 문서인
  `open-questions.md`(24%)와 비슷하거나 높다. 보존한 것: 파일명, `Status:` 줄의 통제 어휘
  (Selected/Accepted/Proposed와 그 수식어), `Date:`, 구조 H2(`## Context`, `## Decision`,
  `## Alternatives`, `## Consequences`, `## Contract synchronization`), ADR-001~013의 이중 언어
  헤딩(`## Context / 배경`), 모든 코드 식별자·경로·상태값·컬럼명·SQL·URL. 번역한 것: 산문, H3
  하위 헤딩, H1 제목.
- 번역 검증: git HEAD와 비교해 23개 파일의 헤딩 구조, 표 행 수, backtick 코드 식별자 집합을
  대조했다. 공백 정규화 후 **식별자 유실 0건**이다. 처음 플래그된 4건은 원문에서 backtick span이
  줄바꿈으로 쪼개져 있던 artifact였다. 헤딩 차이는 의도한 H3·제목 번역뿐이다.
  `python scripts/check_repository.py`가 통과해 문서 간 링크도 전부 유효하다.
- 변경 파일: `docs/adr/ADR-001`~`ADR-023` 23개, `docs/adr/README.md`,
  `docs/architecture/open-questions.md`, `WORKLOG.md`
- 검증: `python scripts/check_repository.py` 통과. 문서 전용 변경이라 Gradle 검증은 실행하지 않았다.
  commit, push는 수행하지 않았다.
- 다음: ADR-023 승인 또는 수정. 특히 종료 전이와 배정 처리, RESIDENT의 `note` 가시성을 확정해야
  한다. 승인 후 Frozen 동기화와 V9 + visit 슬라이스 구현을 진행한다. 이후 작성하는 모든 ADR은
  한국어로 쓴다.

## 2026-11-04 - 신고생성 슬라이스 리뷰 결과 기록, Phase 3 조회 작업 착수

- 브랜치 정리: 로컬 `main`을 `origin/main`(`cd2f4b4`, PR #14 병합)으로 fast-forward하고
  `feature/query-maintenance-request`를 생성했다. `feature/create-maintenance-request`는 전량
  `origin/main`에 포함되어 diff가 비어 있다(병합 완료, 삭제 대기).
- 누락 기록 보완: 이전 10-07 항목들이 "no commit or push was performed"로 끝나 있으나, 실제로는 그 이후
  커밋 `40ff786`/`66f628c` 생성, push, PR #14 생성·병합(`cd2f4b4`)이 수행되었다.
- `7e1d717..66f628c`(신고생성 세로 슬라이스) 코드리뷰를 high 수준으로 실행했다. Frozen baseline 해시 8개
  재계산 일치, ArchUnit 규칙 6개 위반 없음, `P-`→`DF-<id>` 교체의 트랜잭션 내 격리와 응답 최종값,
  권한→404→409→400 순서, V6 CHECK 제약과 앱 검증 일치를 확인했다.
- **사용자 결정: 전체 코드리뷰는 Phase 3 완료 후 인증 슬라이스부터 처음부터 다시 수행한다.** 아래 지적
  8건은 지금 수정하지 않고 deferred 목록으로 보존하며, 일반화한 규칙만 `AGENTS.md`
  "코드 리뷰에서 확정된 구현 규칙"에 반영했다. 신규 코드는 그 규칙을 지킨다.

### Deferred 리뷰 지적 (Phase 3 완료 후 일괄 처리)

1. `maintenance/api/CreateMaintenanceRequest.java:8` — `contactBeforeEntry`가 primitive `boolean`이라
   필드 누락 시 400이 아니라 `false`로 저장된다. `@NotNull Boolean` 필요.
2. `maintenance/application/MaintenanceRequestCreationService.java:134` — `ZoneId.of(dormitory.getTimezone())`
   무방비. timezone은 `@NotBlank @Size(max=50)`만 검증되어 `"KST"` 같은 값이 저장되면 해당 기숙사의 모든
   신고 생성이 계약 외 500으로 실패한다. 동일 패턴 `ResidenceCommandService:158,167`. 근본 수정은 기숙사 생성 검증.
3. `maintenance/api/CreateMaintenanceRequest.java:8` — `description`에 `@Size` 상한 없음. JSON 본문 크기
   제한도 없어 TEXT에 대용량 저장이 가능하다.
4. `maintenance/api/MaintenanceRequestExceptionHandler.java:151` — `DataIntegrityViolationException` 및
   예상 외 예외 fallback 핸들러 없음. rollback 경로가 Spring 기본 에러 본문으로 나가 Frozen 표준 에러
   계약(`code`/`traceId`/`fieldErrors`)을 위반한다. `INTERNAL_ERROR` 핸들러가 레포 전체에 없다.
5. `maintenance/application/MaintenanceRequestCreationService.java:69` — `validateActiveRoom`이 소유권
   `requireCurrentResidence`보다 먼저 실행되고 conflict 메시지가 응답에 노출되어, 입주자가 `spaceId`를 훑어
   전체 space/building/dormitory의 존재·활성 상태를 열거할 수 있다.
6. `maintenance/application/MaintenanceRequestCreationService.java:149` — `entryPolicy()`가
   `IllegalArgumentException`만 잡지만 `Enum.valueOf(null)`은 NPE를 던진다. command record가 null을
   허용하므로 컨트롤러 외 호출자에서는 400 대신 500이 된다.
7. `maintenance/api/MaintenanceRequestExceptionHandler.java:95` — `code()` 기본 반환값이
   `"SPACE_NOT_FOUND"`. 예외 타입 추가 시 분기를 잊으면 컴파일 오류 없이 잘못된 Frozen 코드가 나간다.
8. `maintenance/application/MaintenanceRequestCreationService.java:119` — `validateActiveRoom`이
   `ResidenceCommandService`의 계층 탐색/활성 검사를 중복한다. location 규칙 변경 시 조용히 갈라진다.

- 변경 파일: `AGENTS.md`, `WORKLOG.md`
- 검증: 이번 변경은 문서 전용이다. Gradle 검증은 코드 변경이 없어 실행하지 않았다.
- Phase 3 조회 계약 공백을 조사하고 Proposed `ADR-020`을 작성했다. Frozen API 인벤토리에 세 read
  path가 이미 있으나 가시성 기준, 가시성 밖 단건 조회의 403/404, filter set, 페이징 경계·정렬,
  summary/detail DTO 분리, 상세의 sub-resource 포함 여부, `assigned-to-me`의 종료된 assignment
  처리, scope-less ADMIN 동작이 모두 미정임을 확인했다. `REQUEST_NOT_FOUND`는 이미 Frozen 404
  코드로 존재해 신규 에러 코드는 필요하지 않다.
- ADR-020을 ADR 인덱스에 Proposed로 등록하고 `open-questions.md`에 Q04로 기록했다. Frozen product
  문서와 baseline hash, Java, Flyway, 테스트는 변경하지 않았다.
- 변경 파일: `AGENTS.md`, `WORKLOG.md`, `docs/adr/ADR-020-maintenance-request-query-contract.md`,
  `docs/adr/README.md`, `docs/architecture/open-questions.md`
- 검증: `python scripts/check_repository.py` 통과, `git diff --check` 통과(기존 LF/CRLF 경고만).
  문서 전용 변경이라 Gradle 검증은 실행하지 않았다.
- 사용자 승인(2026-11-04)으로 ADR-020을 Accepted로 전환했다. 가시성 밖 단건 조회는 `404
  REQUEST_NOT_FOUND`, RESIDENT 가시성 anchor는 `reporterId`로 제안대로 확정되었다. Frozen API
  specification과 permission matrix에 read 가시성, 404/403 경계, filter set, 페이징 경계, 고정
  정렬, summary/detail DTO를 동기화하고 baseline hash 2건을 재계산했다. Q04는 resolved다.
- 구현 착수 직전에 ADR-020과 스키마의 불일치를 발견했다. V6에는 assignment 테이블도 `assignee_id`
  컬럼도 없어서 ADR-020의 WORKER 가시성, `GET /maintenance-requests/assigned-to-me`,
  `assigneeId` filter/field를 구현할 수 없다. ADR-020에 이 의존성을 명시했다.
- 사용자 결정: 배정 슬라이스를 먼저 구현하고 조회를 한 번에 완성한다. 조회 코드를 두 번 열지 않기
  위한 선택이다.
- Proposed `ADR-021`(배정 계약)을 작성했다. Frozen은 경로, transition, actor role·scope, ERD의
  `MaintenanceAssignment` 컬럼과 partial unique index까지 이미 정해두었고 error mapping만 명시적으로
  미뤄둔 상태였다. ADR-021은 command DTO, `404 WORKER_NOT_FOUND` / `409 INVALID_WORKER`,
  active assignee 재배정과 unique index 경합의 `409 REQUEST_ALREADY_ASSIGNED`,
  `VERSION_CONFLICT`의 경계, `REQUEST_ASSIGNED`/`REQUEST_REASSIGNED` 분리, V7 마이그레이션
  (V6 미수정, check constraint 교체), worker dormitory 무제약을 제안한다. ADR 인덱스에 Proposed로
  등록하고 `open-questions.md`에 Q05로 기록했다.
- 사용자 승인(2026-11-04)으로 ADR-021을 Accepted로 전환했다. `409 INVALID_WORKER`, duplicate
  assignee와 unique index 경합의 `409 REQUEST_ALREADY_ASSIGNED`, denormalized `assignee_id` 없는
  단일 진실 원천이 제안대로 확정되었다. Frozen API specification, permission matrix, ERD(V7 물리
  규칙)에 동기화하고 baseline hash 3건을 재계산했다. Q05는 resolved다.

### 구현: 배정 슬라이스 + 조회 슬라이스 (한 슬라이스)

- `V7__maintenance_assignment.sql`: ERD대로 `maintenance_assignment`를 만들고 네 FK 모두 ON DELETE
  RESTRICT, partial unique index `ux_assignment_active_request`,
  `(worker_id, assigned_at DESC)` index를 추가했다. `unassigned_at`/`unassigned_by_id` 동반
  NULL 여부와 시간 순서를 CHECK로 보강했다. V6는 수정하지 않고
  `ck_request_history_event_type`만 drop 후 재생성해 `REQUEST_ASSIGNED`/`REQUEST_REASSIGNED`를
  허용했다.
- Domain: `MaintenanceAssignment`(생성·`end`·`isActive`, 종료된 행 보존), `MaintenanceRequest`에
  named lifecycle command `markAssigned`(이전 status 반환)와 `isAssignable`/`matchesVersion`,
  누락된 `status_reason`/`resolved_at`/`closed_at` 매핑과 `getUpdatedAt`을 추가했다. public status
  setter는 없다. `RequestHistory`는 JSONB `metadata`를 받는 생성자를 추가했고 모든 컬럼을
  `updatable = false`로 두어 append-only를 코드에서도 고정했다.
- Application: `MaintenanceRequestAssignmentService`는 한 transaction에서 인가 → version → state →
  worker 검증 → 기존 assignment 종료 → 신규 생성 → `markAssigned` → history 한 행을 수행한다.
  인가를 state·reference 검증보다 먼저 두어 conflict 메시지로 타인 리소스 상태가 새지 않게 했다.
  `MaintenanceRequestQueryService`는 상세/목록/`assigned-to-me`를 제공하고 가시성 밖 상세를 404로
  돌린다. 페이징 경계는 0-based `page`, `1..100` `size`다.
- Infrastructure: 네이티브 가시성 쿼리에 ORDER BY를 포함하고 정렬 없는 `PageRequest`를 넘겨
  Spring Data가 property 이름을 덧붙이지 못하게 했다(Residence 슬라이스가 겪은 결함). 빈
  컬렉션이 네이티브 IN에 들어가지 못하므로 status·dormitory 필터는 sentinel 값을 넘기고 동반
  카운트 파라미터로 필터를 끈다. 목록의 `assigneeId`는 한 번의 배치 조회로 채워 N+1을 피한다.
- API: `GET /{id}`, `GET /maintenance-requests`, `GET /assigned-to-me`,
  `POST /{id}/assignments` 네 경로를 추가했다. `anyRequest().denyAll()`이므로
  `SecurityConfiguration`에 네 경로를 명시 등록했다. 목록 summary는 `description`과 출입 필드를
  제외하고 상세만 노출한다.
- 예외 핸들러: `REQUEST_NOT_FOUND`, `WORKER_NOT_FOUND`, `REQUEST_ALREADY_ASSIGNED`,
  `INVALID_WORKER`, `VERSION_CONFLICT`, 쿼리 400 매핑을 추가했다. 이번 기회에 AGENTS.md에 새로 박은
  규칙도 적용했다. `code()`의 추측성 기본값 `SPACE_NOT_FOUND`를 제거해 미지의 타입은 명시적으로
  실패하게 했고, `DataIntegrityViolationException`·`MethodArgumentTypeMismatchException`·
  `Exception` fallback을 추가해 표준 에러 계약을 우회하는 응답이 나가지 않게 했다.
- 테스트: `MaintenanceAssignmentTest`(도메인 lifecycle), `MaintenanceRequestTest` 확장(전이·version·
  status setter 부재), 핸들러 테스트 확장(신규 코드·fallback·추측성 기본값 거부),
  `MaintenanceRequestAssignmentIntegrationTest`(초기 배정·재배정·IN_PROGRESS 복귀·scope 403·
  404/409 매핑·duplicate assignee·stale version·terminal state·DB partial unique index·rollback),
  `MaintenanceRequestQueryIntegrationTest`(본인/타인 404 동일성·scope 가시성·former worker 읽기·
  역할별 목록·summary projection·assigneeId·정렬·필터·scope 밖 필터 403·scope 없는 ADMIN 403·
  페이징 경계·assigned-to-me active 전환)를 추가했다. `FoundationIntegrationTest`의 마이그레이션
  기대값을 `7`로 갱신하고 `SecurityBoundaryTest`에 신규 서비스 mock 2개를 추가했다.

### 통합 테스트가 잡은 버그: Hibernate flush 순서 vs partial unique index

- 첫 전체 실행은 69 tests / 2 failures였다. 두 실패 모두 재배정이었고, 재배정 요청이 200이 아니라
  409 `REQUEST_ALREADY_ASSIGNED`를 반환했다.
- 원인: Hibernate는 한 flush 안에서 insertion을 update보다 먼저 보낸다. 기존 assignment의
  `end()` UPDATE와 신규 assignment INSERT를 같은 flush에 두면, 기존 행의 `unassigned_at`이 아직
  NULL인 상태로 두 번째 활성 행이 INSERT되어 `ux_assignment_active_request`가 정당한 재배정을
  거부한다. 그 `DataIntegrityViolationException`을 경합으로 간주하는 catch가 409로 바꿨다.
- 수정: 종료된 assignment를 먼저 `saveAndFlush`해 UPDATE를 단독 flush로 내보낸 뒤 신규 행을
  INSERT한다. 마이그레이션, 계약, 에러 매핑은 바꾸지 않았다. 경합을
  `REQUEST_ALREADY_ASSIGNED`로 매핑하는 동작은 그대로 유지된다.

### 검증 결과

- 요청 순서대로 전부 통과했다: `gradlew --stop`(No Gradle daemons are running) →
  `python scripts/check_repository.py` → `git diff --check`(기존 LF/CRLF 경고만) →
  `check bootJar --no-daemon --console=plain` → `integrationTest --no-daemon --console=plain`.
- 최종 integrationTest 집계: **17 suites, 69 tests, 0 failures, 0 errors, 0 skipped.** 신규 기여는
  배정 10, 조회 12다. Docker Desktop을 기동한 뒤 PostgreSQL Testcontainers로 실행했다.
- 단위 `check`는 Checkstyle(main/test/integrationTest)과 ArchUnit을 포함해 통과했다.
- 캐시나 빌드 산출물을 삭제하지 않았고 commit, push, reset, checkout을 수행하지 않았다.

### 구현: lifecycle command 슬라이스 (ADR-022)

- 사용자 승인(2026-11-04)으로 ADR-022를 Accepted로 전환했다. 종료 전이가 active assignment를
  종료하고 `resolve`는 유지하는 결정이 제안대로 확정되었다. Frozen API specification, permission
  matrix, ERD(V8 물리 규칙과 duplicate chain 금지)에 동기화하고 baseline hash 3건을 재계산했다.
  Q06은 resolved다.
- `V8__maintenance_request_lifecycle.sql`: `ck_request_history_event_type`을 11개 event type으로
  교체하고(V6/V7 미수정), `duplicate_of_id` 자기참조 금지, status와 `duplicate_of_id`의 동반 조건,
  `closed_at`은 `resolved_at` 이후라는 CHECK 3개와 `(status, resolved_at)` index를 추가했다.
- Domain: `MaintenanceRequest`에 8개 named command(`start`/`hold`/`resume`/`resolve`/`close`/
  `reopen`/`reject`/`markDuplicate`)와 `isIn`, 단일 `transition` 헬퍼를 추가했다. `transition`이
  status 변경의 유일한 지점이며 `statusReason`을 매번 덮어쓰거나 지운다. public status setter는
  없다. `resolve`가 `resolvedAt`, `close`가 `closedAt`을 쓰고 `reopen`은 `resolvedAt`만 지운다.
- Application: `MaintenanceRequestLifecycleService`가 8개 전이를 제공한다. 네 worker 전이는 source
  state, event type, 호출할 named command만 다르므로 `Transition` 함수형 인터페이스로 한 본문을
  공유한다. 인가 → version → state 순서를 지키고 `Authority` enum으로 세 권한 유형을 나눈다.
  `WorkerNotAssignedException`을 추가해 `403 WORKER_NOT_ASSIGNED`와 `ACCESS_DENIED`를 분리했다.
- API: 8개 POST 엔드포인트, `LifecycleCommandRequest`/`MarkDuplicateRequest`,
  `SecurityConfiguration`에 8개 경로 명시 등록(`anyRequest().denyAll()` 때문), 핸들러에
  `WORKER_NOT_ASSIGNED` 매핑을 추가했다.
- `SecurityBoundaryTest`의 `skeletonNeverPretendsBusinessAuthorizationIsImplemented`는 `/resolve`가
  미구현이라는 전제로 403을 단언했다. ADR-022로 구현되었으므로 단언을 약화시키지 않고 아직
  미구현인 frozen 경로(visits/work-logs/comments/attachments, PATCH `/{id}`)로 의도를 옮겼고,
  구현된 lifecycle 경로가 deny-all에 더는 막히지 않는다는 테스트를 별도로 추가했다.

### 통합 테스트가 잡은 3건

- 첫 전체 실행은 83 tests / 3 failures였고 원인이 각각 달랐다. 2건은 테스트가 틀렸고 1건은 계약의
  허점을 드러냈다.
- (1) `resolvedAndTerminalStatesCannotBeAssigned`가 `duplicate_of_id` 없이 status를 DUPLICATE로
  강제해 V8의 `ck_maintenance_request_duplicate_status`에 걸렸다. 제약이 의도대로 동작한 것이므로
  테스트가 original을 함께 설정하도록 고쳤다.
- (2) `terminalStatesRefuseEveryFurtherCommandIncludingForASuperAdmin`이 모든 명령에 409를
  기대했으나 worker 전용 4개는 403이었다. 인가가 state보다 먼저 평가되고 SUPER_ADMIN은 WORKER
  role이 없으므로 올바른 동작이다. 테스트를 worker 명령 403 / admin·reporter 명령 409로 분리해
  이 순서를 명시적으로 검증하게 했다.
- (3) `reject`를 ASSIGNED 건에 호출해 409가 났다. frozen matrix가 `reject`를 REPORTED로 한정하기
  때문이다. 여기서 드러난 사실: **REPORTED 건은 active assignment를 가질 수 없으므로**(배정이 같은
  transaction에서 ASSIGNED로 옮긴다) "close/reject/mark-duplicate가 배정을 종료한다"는 규칙은 실제로
  `close`에만 적용된다. 규칙 자체는 세 전이 모두에 그대로 유지했다. 불변식이 그 우연에 의존하지
  않게 하고, 나중에 source state를 넓히는 슬라이스가 공백 대신 올바른 동작을 물려받게 하기
  위해서다. 이 관찰을 ADR-022 Consequences에 기록했다. 계약과 Frozen 문서는 바꾸지 않았다.

### lifecycle 슬라이스 검증 결과

- 요청 순서대로 전부 통과했다: `gradlew --stop`(No Gradle daemons are running) →
  `python scripts/check_repository.py` → `git diff --check`(기존 LF/CRLF 경고만) →
  `check bootJar --no-daemon --console=plain` → `integrationTest --no-daemon --console=plain`.
- 최종 integrationTest 집계: **18 suites, 83 tests, 0 failures, 0 errors, 0 skipped.** lifecycle
  신규 기여는 14다. 단위 `check`는 Checkstyle 3종과 ArchUnit 포함 통과했다.
- 캐시나 빌드 산출물을 삭제하지 않았고 commit, push, reset, checkout을 수행하지 않았다.

### 남은 작업과 주의사항

- 커밋되지 않은 변경이 `feature/query-maintenance-request`에 남아 있다. 커밋·push·PR은 요청 시에만 한다.
- Phase 3 남은 범위: 신고 metadata PATCH, visit, work log, attachment, comment. 전부 계약 확인 →
  필요하면 ADR → 승인 → 구현 순서를 지킨다. 상태 전이 8개는 ADR-022로 완료했다.
- RequestHistory event type을 늘리는 슬라이스는 `ck_request_history_event_type`을 또 교체해야 한다.
  V7과 V8이 그 선례다.
- 전체 코드리뷰는 사용자 결정대로 Phase 3 완료 후 인증 슬라이스부터 처음부터 수행한다. 위
  deferred 목록 8건이 그 대상이며, 이번 슬라이스에서 4번(fallback 핸들러)과 7번(추측성 기본값)은
  신규 코드 기준으로 이미 해소했다. 1·2·3·5·6·8번은 생성 슬라이스에 그대로 남아 있다.

## 2026-10-06 - MaintenanceRequest creation vertical slice completion follow-up

- Completed the ADR-019 create slice currently present on `feature/create-maintenance-request`:
  V6 adds `maintenance_request` and append-only `request_history`, including enum checks,
  referential constraints, request/history lookup indexes, and the `REQUEST_CREATED` event check.
- The maintenance API/application/domain/infrastructure packages provide only
  `POST /api/v1/maintenance-requests`.  The service owns one transaction: it verifies the JWT
  RESIDENT role, current active Residence ROOM and active location hierarchy, category state and
  facility-space/state rules; allocates the identity, assigns `DF-<id>`, and stores exactly one
  `REQUEST_CREATED` history row. `OUT_OF_SERVICE` is accepted and `RETIRED` is rejected.
- Added typed 400 mapping for invalid entry policy and invalid preferred time, standard trace IDs
  in feature errors, Clock-based current-residence/time handling, and a SecurityBoundary MVC mock
  for the newly registered controller dependency. Existing domain and Testcontainers API tests
  cover the supplied success, authorization/conflict, facility-state, missing-resource and
  malformed-entry-policy paths.
- Verification: `compileJava compileTestJava` completed successfully before the final MVC-test
  mock adjustment; `checkstyleMain checkstyleTest` produced no Checkstyle errors. The initial
  `check bootJar` executed 44 tests and found only the missing MVC mock (now fixed). The targeted
  rerun could not compile because Windows denied access to the pre-existing Gradle cache JAR
  `spring-security-oauth2-resource-server-6.5.11.jar`; it is an environment/cache access failure,
  not a passing gate. `check bootJar`, `integrationTest`, and repository guard remain required.
- No commit or push was performed. Remaining product scope is intentionally unchanged: request
  reads/listing/PATCH, assignment, lifecycle transitions, visits, attachments and comments are
  not implemented.

## 2026-10-02 - MaintenanceRequest creation vertical slice

- Implemented the approved ADR-019 create slice only: V6 creates `maintenance_request` and
  append-only `request_history`; the new maintenance feature owns the request/history aggregates,
  boundary ports/adapters, and `POST /api/v1/maintenance-requests`.
- Creation is one transaction: validate RESIDENT/current Residence ROOM and active references,
  allocate the request ID, set `DF-<id>`, and append exactly one `REQUEST_CREATED` history row.
  Category default priority and initial `REPORTED` status are server-owned. Facility space mismatch
  and RETIRED state return `409 INVALID_REQUEST_STATE`; OUT_OF_SERVICE remains allowed.
- Added a domain unit test and PostgreSQL Testcontainers API regression coverage for successful
  creation/history, role/current-residence/facility conflicts, OUT_OF_SERVICE acceptance, missing
  references, and malformed EntryPolicy. Listing/detail/PATCH, assignment, lifecycle commands,
  visits, attachments, and comments remain unimplemented.
- Verification so far: `compileJava` passed and `git diff --check` passed. Gradle test tasks were
  attempted but did not return a completion status in the execution window after compiling test
  classes; `gradlew --stop` reported no running daemon. Full `check bootJar` and `integrationTest`
  remain to be run to completion.
- No commit or push was performed.

## 2026-10-02 - ADR-019 V1 approval and Frozen contract synchronization

- Human approval accepted ADR-019. V1 reporting is limited to an authenticated RESIDENT's current
  Residence ROOM; common-area and another resident's room reporting are not supported. A supplied
  `OUT_OF_SERVICE` Facility remains reportable, a `RETIRED` Facility is denied, and the public
  request number is the server-generated `DF-<id>` value.
- Synchronized the exact create DTO, server-owned initial values, reference checks, preferred-time
  rule, response, 400/404/409 semantics, and atomic `REQUEST_CREATED` RequestHistory row into the
  Frozen API, permission matrix, and ERD. Q03 is resolved and ADR-019 is Accepted.
- No Java source, test, Flyway migration, build file, commit, or push was changed.
- Changed files: `docs/adr/ADR-019-maintenance-request-creation-contract.md`,
  `docs/adr/README.md`, `docs/architecture/open-questions.md`,
  `docs/product/api-spec-v1.md`, `docs/product/permission-state-matrix-v1.md`,
  `docs/product/erd-v1.md`, `PROJECT_CONTEXT.md`, `WORKLOG.md`, and the Frozen hash manifest.
- Verification: `python scripts/check_repository.py`, `python scripts/test_check_repository.py`,
  and `git diff --check` passed. `backend` `gradlew check bootJar` could not start because the
  pre-existing modified `backend/build.gradle` begins with non-Gradle text and fails parsing at
  line 1; Gradle also reported denied access to its existing problems report. `integrationTest`
  was not run because it uses the same broken build script.
- Next: implement the request-create vertical slice only in a separately requested change; it must
  follow ADR-019 and preserve the create transaction's RequestHistory row.

## 2026-10-02 - Phase 3 create-request contract proposal

- Phase 2 merge commit `7e1d717`을 기준으로 `feature/create-maintenance-request`를 생성했다.
- Frozen V1가 `POST /maintenance-requests`의 DTO, reportable Space authorization,
  reference state, request-number, preferred-time, and initial-history semantics를 정하지
  않은 것을 확인했다. 추측 구현을 피하기 위해 Proposed ADR-019와 open question Q03을
  추가했다.
- ADR-019는 RESIDENT current-residence room reporting, server-owned `REPORTED` status와
  category default priority, `DF-<id>` request number, retired facility denial / out-of-service
  facility reporting, preferred-time range, 그리고 atomic `REQUEST_CREATED` history를
  제안한다.
- No Java, Flyway, Frozen product document, frozen hash, test, commit, or push was changed.
- Next: human approval or revision of ADR-019; only then synchronize Frozen documents and
  implement the request-create vertical slice.

## 2026-10-01 - Phase 2 final quality gate passed

- Final Residence pageable-query review fixed PostgreSQL native SQL date casting and prevented
  Spring Data from appending Java property sort names to the native `start_date`/`id` ordering.
  SecurityBoundaryTest now supplies the new query-service dependency in its MVC slice.
- Verification passed: `compileJava`, `test`, targeted Residence Testcontainers integration,
  `check bootJar`, full `integrationTest` (14 suites, 40 tests, 0 failures, 0 errors, 0 skipped),
  `python scripts/check_repository.py`, and `git diff --check`.
- No commits, pushes, resets, stashes, checkouts, or build-result deletion were performed.
- Phase 3 handoff: preserve the approved Phase 2 structure/Residence contracts, scope boundary,
  explicit command endpoints, and Residence exclusion constraint; do not expand them while adding
  MaintenanceRequest workflow behavior.

## 2026-10-01 - Phase 2 Residence contract alignment and audit

- Aligned Accepted ADR-018 and frozen Residence contract with current-only creation:
  `CreateResidenceRequest` now has only `residentId`, `roomSpaceId`, and `startDate`; closed
  historical creation and create-time `endDate` are not a V1 path. The ADR now records allowed
  two-command same-day end-then-create movement.
- Added the admin filtered page endpoint and RESIDENT self page endpoint. Both use stable
  `startDate DESC, id DESC` ordering, page/size bounds, per-room Dormitory scope filtering, and
  the required admin filter rule. Scope-less ADMIN and explicit out-of-scope room filters are 403.
- Preserved V5 PostgreSQL `btree_gist` exclusion protection and added named-constraint mapping to
  `409 RESIDENCE_PERIOD_OVERLAP`. The Residence Testcontainers test now also directly exercises
  the database exclusion constraint.
- Synchronized `erd-v1`, `api-spec-v1`, permission matrix, database/security architecture notes,
  open-question register, and frozen baseline hashes. `python scripts/check_repository.py` and
  `git diff --check` passed.
- Verification: `gradlew test --no-daemon --console=plain` and `gradlew check bootJar --no-daemon
  --console=plain` passed using local Temurin 21. Targeted `ResidenceIntegrationTest` passed
  against PostgreSQL Testcontainers (1 test, 0 failures/errors). Full `integrationTest` was
  attempted; its first attempt failed because an already-running DormFix Gradle worker held
  `build/test-results/integrationTest/binary/output.bin`. No files were removed; `gradlew --stop`
  stopped the proven DormFix daemon. The subsequent full run exceeded the tool execution window,
  so full-suite completion remains unverified.
- Remaining: split and expand Residence integration coverage for all role, pagination, invalid
  input, inactive hierarchy, same-day move, and multi-resident scenarios before declaring Phase 2
  complete. Re-run the full `integrationTest` to completion after ensuring no prior Gradle worker
  is active. Do not update PROJECT_CONTEXT to Phase 2 complete until that gate and coverage pass.

## 2026-09-30 - Phase 2 contract proposals; implementation held

- Added proposed ADR-017 for MaintenanceCategory reactivation and proposed ADR-018 for Residence
  management. They contain the exact proposed API, role/scope authorization, state/period rules,
  error meanings, alternatives, and persistence direction.
- Updated `docs/architecture/open-questions.md` to distinguish these approval-gated proposals from
  the existing Frozen contract. Updated the ADR index to label ADR-017/018 as Proposed.
- No Java code, Flyway migration, test, Frozen product document, frozen hash, commit, push, or
  branch state was changed in this design pass.
- Human approval is required before contract synchronization or implementation. In particular,
  approve the Residence half-open date model, current-ending behavior, no-future-reservation
  policy, move flow, role matrix, and PostgreSQL exclusion-constraint direction; also approve the
  explicit Category reactivate command.
- Verification: document-only `python scripts/check_repository.py` and `git diff --check` passed.
  Do not treat the existing implementation as contract authority.

## 2026-09-30 - Phase 2 structure-management completion pass

- Completed Facility administration vertical slice: scoped ADMIN/SUPER_ADMIN create and metadata PATCH,
  plus explicit out-of-service, reactivate, and retire commands. Facility PATCH cannot bind status and
  RETIRED remains terminal.
- Completed MaintenanceCategory administration vertical slice: SUPER_ADMIN create, allow-listed metadata
  PATCH, explicit deactivate/reactivate, immutable create-only parentId, and inactive-parent protection
  for child creation/reactivation.
- Preserved V3/V4 migrations, existing structure read APIs, and Dormitory/Building/Space admin APIs.
  Security now authenticates `/api/v1/admin/**` and leaves the Facility scope decision in the application
  command service; the existing SUPER_ADMIN-only commands retain their application checks.
- Added PostgreSQL/Testcontainers API coverage for Facility scope/lifecycle, Category authorization,
  parentId patch rejection, and inactive parent conflicts. The suite also exercises the existing V3/V4
  migration path.
- Changed areas: `backend/src/main/java/com/dormfix/catalog/**`, Facility/Category ports and domain models,
  `SecurityConfiguration`, structure error advice, security boundary test, and catalog integration test.
- Verification: `backend` `gradlew test`, `gradlew check bootJar`, and `gradlew integrationTest` passed
  with the repository JDK 21 and PostgreSQL Testcontainers; see current handoff for final guard results.
- Remaining decision: Residence cannot be implemented safely. Frozen ERD leaves date boundary/overlap open,
  while frozen API lacks Residence paths, DTOs, authorization, close/replace history semantics, and error
  mapping. Recorded in `docs/architecture/open-questions.md`. Category reactivate is required by this
  Phase request and parent-reactivation policy but absent from the frozen API table; recorded for approved
  contract synchronization.
- Phase 3 handoff: do not assume Residence semantics or silently promote category reactivate into the
  frozen API contract. Maintenance Request work must consume Structure IDs and preserve the aggregate,
  authorization, and lifecycle invariants in `AGENTS.md`.

## 2026-09-20 - Admin structure API contract frozen

- 사용자가 승인한 Admin API 계약을 ADR-016으로 확정했다.
- API inventory에 Dormitory/Building/Space/Facility/MaintenanceCategory 관리 path와 allow-listed DTO를 기록했다.
- Dormitory name은 V1 unique가 아니며 duplicate-name 409를 제거했다.
- Facility command path를 `/out-of-service`, `/reactivate`, `/retire`로 확정하고 `RETIRED` terminal 전이를 문서화했다.
- MaintenanceCategory `parentId`는 create-only로 확정했으며 category move API는 V1에서 제공하지 않는다.
- API, permission matrix, security guidance, open-question register, ADR index 및 PROJECT_CONTEXT를 동기화했다.
- 변경 파일: `docs/product/api-spec-v1.md`, `docs/product/permission-state-matrix-v1.md`, `docs/adr/ADR-015-structure-management-authorization.md`, `docs/adr/ADR-016-admin-structure-api-contract.md`, `docs/adr/README.md`, `docs/architecture/security.md`, `docs/architecture/open-questions.md`, `PROJECT_CONTEXT.md`, `WORKLOG.md`, `docs/product/frozen-baseline.sha256.json`
- Java Controller/Application Service는 구현하지 않았다.
- 검증: `python scripts/check_repository.py` 통과; `git diff --check` 통과. Java/Gradle/Docker 검증은 요청 범위 밖이라 실행하지 않았다.

이 파일은 작업 간 인수인계를 위한 누적 로그다. 최신 기록을 위에 추가하고, 이미 기록된 내용을 임의로 지우지 않는다.

## 현재 handoff — 2026-09-14

- 현재 브랜치: `main`
- Authentication Phase 1 commit `2984092b48ff4d7cb1cffa4507492b2777a5c79c`를 생성하고 `origin/feature/authentication`에 push한 뒤 `main`에 fast-forward merge했다.
- `main`도 같은 commit으로 `origin/main`에 push했다.
- repository guard, Gradle `test`, Checkstyle, ArchUnit, PostgreSQL Testcontainers `integrationTest`, `check bootJar` 검증을 통과했다.
- 현재 남은 working-tree 변경은 이 handoff 파일을 commit하기 전의 `AGENTS.md`, `PROJECT_CONTEXT.md`, `WORKLOG.md`뿐이다.
- 다음 작업은 이 파일과 `AGENTS.md`, `docs/README.md`를 먼저 읽고, 사용자가 요청한 범위에서만 진행한다.

이 아래의 과거 기록은 각 기록 당시의 상태를 보존한 historical log다. 현재 남은 작업과 브랜치 상태는 이 handoff와 최신 날짜 항목을 기준으로 확인한다.

## 2026-09-13 — Login vertical slice와 JWT access-token issuance 구현

### 수행한 작업

- `POST /api/v1/auth/login`을 공개 endpoint로 추가하고 email trim/lowercase normalize, 사용자 조회, password 검증, ACTIVE 상태 검증을 구현했다.
- 성공한 login transaction에서 `last_login_at`과 `updated_at`을 같은 초 단위 시각으로 갱신한다.
- application의 `AccessTokenIssuer` port와 infrastructure의 Spring Security JOSE/Nimbus RS256 adapter를 추가했다.
- JWT에 key ID, issuer, audience, subject user ID, roles, `iat`, `nbf`, 30분 `exp`, `jti`를 기록한다.
- RSA X.509 DER public key와 PKCS#8 DER private key의 Base64 값, issuer, audience, key ID를 환경설정으로만 받도록 구성했다.
- 존재하지 않는 email, 잘못된 password, SUSPENDED/WITHDRAWN 상태를 동일한 `401 INVALID_CREDENTIALS`로 처리하여 계정 상태를 노출하지 않는다.
- login request/response DTO, API/application/infrastructure 테스트와 실제 RSA signature 검증을 포함한 PostgreSQL Testcontainers 테스트를 추가했다.

### 변경한 파일

- `backend/build.gradle`
- `backend/src/main/java/com/dormfix/identity/api/LoginController.java`
- `backend/src/main/java/com/dormfix/identity/api/LoginExceptionHandler.java`
- `backend/src/main/java/com/dormfix/identity/api/LoginRequest.java`
- `backend/src/main/java/com/dormfix/identity/api/LoginResponse.java`
- `backend/src/main/java/com/dormfix/identity/application/AccessTokenIssuer.java`
- `backend/src/main/java/com/dormfix/identity/application/IssuedAccessToken.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginCommand.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginRejectedException.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginResult.java`
- `backend/src/main/java/com/dormfix/identity/application/LoginService.java`
- `backend/src/main/java/com/dormfix/identity/domain/User.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/JwtAccessTokenIssuer.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/JwtConfiguration.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/JwtProperties.java`
- `backend/src/main/java/com/dormfix/identity/infrastructure/SpringDataUserRepository.java`
- `backend/src/main/java/com/dormfix/platform/config/SecurityConfiguration.java`
- `backend/src/test/java/com/dormfix/identity/api/LoginControllerTest.java`
- `backend/src/test/java/com/dormfix/identity/application/LoginServiceTest.java`
- `backend/src/integrationTest/java/com/dormfix/identity/api/LoginIntegrationTest.java`
- `backend/src/integrationTest/java/com/dormfix/test/TestJwtKeys.java`
- 기존 full-context/security 테스트와 identity package 문서를 JWT 설정에 맞춰 갱신했다.
- `.env.example`, `compose.yml`, `infra/production/app.env.example`
- `docs/architecture/security.md`, `docs/development/local-development.md`

### 검증한 내용

- `python scripts/check_repository.py`: 통과
- `backend/gradlew check bootJar`: 통과
- `backend/gradlew integrationTest --rerun-tasks`: PostgreSQL 17.6 Testcontainers에서 13개 통과, 실패/skip 0
- RS256 signature, `kid`, issuer, audience, subject, roles, `iat`/`nbf`, 30분 expiry와 DB `last_login_at` 갱신을 검증했다.
- 첫 integration 실행에서 JWT NumericDate의 초 단위와 response Instant의 nanosecond 차이로 1개 assertion이 실패했다. Login command 시각을 초 단위로 정규화한 뒤 전체 재실행이 통과했다.
- `docker compose config --quiet`: 최종 통과. 첫 검증 helper는 host PowerShell의 RSA DER export API 부재로 실패했으며 비밀이 아닌 Base64 placeholder로 Compose interpolation만 재검증했다.
- `docker build -t dormfix:login-validation backend`: 통과하며 이미지 내부 `check bootJar`도 통과했다.
- private key나 실제 credential이 저장소에 기록되지 않았음을 확인했다.

### 남은 작업

- JWT bearer authentication filter/resource-server verification
- `/api/v1/me`
- refresh-token 발급·rotation endpoint와 login response의 refresh token
- logout/revocation endpoint

### 다음 작업자가 알아야 할 주의사항

- 현재 login response는 이번 요청 범위에 따라 access token만 반환한다. 확정 정책의 refresh token 반환은 refresh slice에서 원자적으로 완성해야 한다.
- app 실행에는 `DORMFIX_SECURITY_JWT_ISSUER`, `DORMFIX_SECURITY_JWT_AUDIENCE`, `DORMFIX_SECURITY_JWT_KEY_ID`, public/private Base64 DER key 설정이 필요하다.
- bearer verification은 아직 연결하지 않았으므로 signup/login 외 business traffic은 계속 deny한다.
- signing key와 token/password는 로그, error, fixture 파일에 남기지 않는다.

## 2026-09-13 — 작업 기억 자동 이어받기 체계 추가

### 수행한 작업

- 이전 작업을 별도 명령 없이 이어받을 수 있도록 프로젝트 기억 문서 체계를 추가했다.
- `AGENTS.md`에 작업 시작 시 자동 확인하고 종료 시 로그를 갱신하는 규칙을 추가했다.

### 변경한 파일

- `AGENTS.md`
- `PROJECT_CONTEXT.md`
- `WORKLOG.md`

### 검증한 내용

- 현재 브랜치: `feature/authentication`
- 기존 사용자 변경사항 3개가 있었으며 보존했다.
- 최근 커밋과 `docs/README.md`의 문서 권위 체계를 확인했다.

### 남은 작업

- 없음. 이후 모든 작업에서 이 파일의 최신 기록을 먼저 확인한다.

### 다음 작업자가 알아야 할 주의사항

- 기존 변경사항을 덮어쓰지 않는다.
- Product V1 frozen 문서와 `docs/architecture/open-questions.md`를 우선한다.
- 작업 종료 시 이 로그에 새 날짜별 항목을 추가한다.
-
## 2026-09-14 - Login slice final verification follow-up

- Added fail-fast validation that JWT public/private RSA keys use the same modulus.
- Re-ran repository checks, Gradle check/bootJar, and PostgreSQL Testcontainers integration tests; all passed.
- Rebuilt the Docker image and revalidated Compose configuration successfully.
- Remaining scope is unchanged: bearer verification/filter, /me, refresh/rotation, and logout are not implemented.
- 2026-09-14 follow-up: implemented RS256 Bearer validation and `GET /api/v1/me`; full Docker `check bootJar` passed, and all 15 PostgreSQL Testcontainers integration tests passed.
- Bearer integration coverage includes missing token, expired token, future `nbf`, invalid signature, issuer and audience failures returning 401. Refresh/logout remain deferred.
- 2026-09-14 refresh follow-up: login now issues a 32-byte URL-safe opaque refresh token and stores only its SHA-256 hash with a 14-day expiry.
- Added `/api/v1/auth/refresh` rotation: the existing session is revoked before a replacement session is saved; revoked, expired, reused, and non-ACTIVE-user tokens are rejected with 401. Raw refresh tokens are not logged or persisted.
- Docker `check bootJar` passed and all 17 PostgreSQL Testcontainers integration tests passed. Logout remains deferred.
- 2026-09-14 logout follow-up: added `POST /api/v1/auth/logout`; it hashes the supplied refresh token, revokes the matching session, and returns 204. Access tokens remain stateless with no blacklist.
- Added regression coverage proving logout makes the same refresh token fail at `/api/v1/auth/refresh`; raw refresh tokens are not logged or persisted. Full PostgreSQL Testcontainers integration suite passed: 18 tests.
- 2026-09-14 path-only cleanup: moved `TokenPairResult.java` from `identityapplication` to `identity/application`; file bytes and package declaration were preserved.
- 2026-09-16: reviewed authoritative Dormitory/Building/Space/Facility/MaintenanceCategory ERD, API, database, permission, and scope documents for `feature/manage-dormitory-structure`; no code or migration was added. Management write DTO/path details, nullability/defaults, FK delete actions, and dormitory scope remain unresolved where documented.
- 2026-09-16: confirmed V1 physical rules for the dormitory structure and added `V3__dormitory_structure.sql` for Dormitory → Building → Space → Facility plus MaintenanceCategory self-reference. PostgreSQL 17.6 SQL application, repository guard, and diff check passed; Java code was not added.
- 2026-09-16 Phase 2 handoff: implemented only JPA domain models and string enums for Dormitory, Building, Space, Facility, and MaintenanceCategory under `location.domain`/`catalog.domain`; all aggregate FKs remain scalar `Long` IDs with no bidirectional associations, CascadeType.ALL, or eager loading. `test checkstyleMain checkstyleTest` passed in Docker JDK. `integrationTest` reached the existing `FoundationIntegrationTest` and failed because its migration version assertion still expects `2` after V3; no application failure was established before the run was interrupted. Next step is to update only V3-related test assertions and rerun PostgreSQL Testcontainers validation. No Repository/API/Application Service was added.
- 2026-09-16 Phase 2 continuation: updated V3-related FoundationIntegrationTest assertions and added PostgreSQL JPA mapping coverage. Full `integrationTest` and Gradle `check` passed in Docker JDK; repository guard and diff check passed. No Repository/API/Application Service was added.
- 2026-09-16: implemented the six frozen Dormitory Structure GET endpoints with read-only application services and explicit response DTOs; entities are not returned at the HTTP boundary. Added authenticated access, stable 404 error responses, and PostgreSQL Testcontainers API coverage. Gradle `check`, full `integrationTest`, repository guard, and diff check passed. Admin create/update/deactivate APIs remain unimplemented.
- 2026-09-18: accepted ADR-014 and resolved Q02 for V1 administrator scope storage using `admin_dormitory_scopes`; added immutable `V4__admin_dormitory_scopes.sql` with composite PK, explicit RESTRICT FKs, and reverse lookup index. Added PostgreSQL Testcontainers coverage for schema objects, duplicate PK rejection, and orphan FK rejection. No Java Entity/Repository/API was added.
- 2026-09-18 verification: Docker-backed PostgreSQL `integrationTest` passed. Initial attempts without a compatible JAVA_HOME were environment failures only; rerun with temporary JDK 21 completed successfully.
- 2026-09-18: implemented scalar-ID JPA model and minimal Repository adapter for `admin_dormitory_scopes` using `AdminDormitoryScopeId`/`@EmbeddedId`; added PostgreSQL Testcontainers coverage for save/query/delete and composite-PK duplicate rejection. `checkstyleMain`, `checkstyleTest`, targeted scope integrationTest, and full integrationTest passed. No scope authorization service or Admin API was added.
- 2026-09-18: added `DormitoryScopeAuthorizationService` for SUPER_ADMIN global access and ADMIN explicit-scope access; scope-less ADMIN, RESIDENT, and WORKER are denied via an `AccessDeniedException` with the existing "Access is denied." contract. Added role/scope unit and PostgreSQL integration coverage. Admin CRUD/API remains unimplemented.
- 2026-09-18: accepted ADR-015 and synchronized the Frozen permission matrix, API inventory, security guidance, and open-question register. Confirmed SUPER_ADMIN-only Dormitory/Building/Space management, scoped ADMIN/SUPER_ADMIN Facility management, SUPER_ADMIN-only global MaintenanceCategory, no parent deactivation propagation, inactive-parent creation/reactivation blocking, and no hard delete. Updated the canonical frozen baseline hashes; no Java or migration changes.
- 2026-09-18: implemented `DormitoryScopeResolutionService` for Space → Building → Dormitory and Facility → Space → Building → Dormitory resolution, delegating resolved IDs to existing scope authorization. Added Facility ID lookup port/adapter, `FACILITY_NOT_FOUND` mapping, unit coverage for path/delegation/404, and PostgreSQL integration coverage for 403/404. Admin Controller/CRUD remains unimplemented.
- 2026-09-18 verification: resolved the initial ArchUnit catalog/location cycle by introducing the narrow `FacilityScopeLookup` application port and catalog infrastructure adapter. Full unit tests, ArchitectureTest, Checkstyle, targeted and full PostgreSQL integrationTest passed.
- 2026-09-23: implemented ADR-016 Admin write APIs for Dormitory, Building, and Space only. Added create, metadata PATCH, explicit deactivate, and explicit reactivate commands; all require SUPER_ADMIN at the Security and application boundaries. PATCH DTOs are allow-listed and reject direct `active` changes. Creation/reactivation below inactive parents returns 409 `INACTIVE_PARENT`; parent deactivation does not cascade. Added unit tests and PostgreSQL Testcontainers API coverage for authorization, lifecycle commands, active-field protection, and inactive-parent conflicts. Unit/API compilation, ArchitectureTest, and Checkstyle passed. PostgreSQL integrationTest compiled but could not run because Docker daemon was unavailable in the environment.

## 2026-10-07 - MaintenanceRequest creation quality-gate handoff

- Ran the requested gate sequence on `feature/create-maintenance-request` without deleting Gradle caches or build outputs. With the repository-local JDK 21 and Gradle home, `gradlew --stop` completed (`No Gradle daemons are running`), `python scripts/check_repository.py` passed, and `git diff --check` passed (only existing LF/CRLF warnings). `check bootJar --no-daemon --console=plain` passed before the follow-up fix.
- The first `integrationTest` attempt failed before test execution because Docker Desktop was stopped; all 15 suites reported Testcontainers `initializationError`. Docker Desktop was started, `docker version` then confirmed the `desktop-linux` engine, and the full integration suite was rerun.
- The Docker-backed rerun executed 15 suites / 43 tests. Only `MaintenanceRequestCreationIntegrationTest` had failures: 2 failures, 0 errors, 0 skipped. Both successful-create assertions received the downstream 403/500 response caused by `maintenance_request.request_number` being NULL on the first IDENTITY insert; the log records PostgreSQL SQLState 23502 and the failing `saveAndFlush` at `MaintenanceRequestCreationService.java:84`.
- Minimal code fix applied, without changing V6 or any migration: `MaintenanceRequest` now uses the existing PostgreSQL identity backing sequence via JPA `SEQUENCE` allocation; `MaintenanceRequestRepository` exposes concrete `save`; creation saves to allocate the ID, assigns `DF-<id>`, then flushes. Changed files: `backend/src/main/java/com/dormfix/maintenance/domain/MaintenanceRequest.java`, `backend/src/main/java/com/dormfix/maintenance/application/MaintenanceRequestRepository.java`, and `backend/src/main/java/com/dormfix/maintenance/application/MaintenanceRequestCreationService.java`.
- Post-fix targeted integration-test compilation was blocked by a recurring Windows access denial for the pre-existing Gradle cache file `C:\Users\phili\Desktop\DORMFIX\.tools\gradle-home\caches\modules-2\files-2.1\org.springframework.security\spring-security-oauth2-resource-server\6.5.11\7f28ddc2ffb134af69da3f9efb3e27ac1e50db4f\spring-security-oauth2-resource-server-6.5.11.jar`. Per request, do not delete/repair the cache or kill unknown processes; next continuation should diagnose the locking process/file only, then resume the gate if the environment is clear.
- No commit, push, reset, checkout, cache deletion, or build-result deletion was performed. Remaining work: diagnose the JAR lock, update this log with the result, then rerun the complete requested sequence from `gradlew --stop` through the full `integrationTest` and record final suite/test/failure/error/skip totals.

## 2026-10-07 - MaintenanceRequest creation quality gate completed

- Resumed from the handoff above. The reported resource-server JAR access denial was diagnosed read-only: no Java/Gradle process was running, `exclusive-read-open` on the exact JAR succeeded, ACLs grant the user and `CodexSandboxUsers` Modify access, and `openfiles /query` could not enumerate handles because system open-file tracking requires elevation. No cache file was deleted or repaired; Gradle was run in an authorized context thereafter.
- The first post-fix attempt using JPA `SEQUENCE` was rejected by Hibernate schema validation because V6 creates an identity column but no separately visible `maintenance_request_id_seq`. That mapping was reverted. Final minimal fix keeps `GenerationType.IDENTITY`, initializes a private 30-character pending request number for the first NOT NULL insert, and replaces it with `DF-<id>` in the same transaction before the final flush. No migration was changed.
- Targeted `MaintenanceRequestCreationIntegrationTest` passed all 3 tests after the final fix.
- Final requested sequence passed in order: `gradlew --stop` (no daemons), `python scripts/check_repository.py`, `git diff --check`, `check bootJar --no-daemon --console=plain`, and full `integrationTest --no-daemon --console=plain`.
- Final integrationTest XML aggregation: 15 suites, 43 tests, 0 failures, 0 errors, 0 skipped. The maintenance creation suite contributed 3/3 passing tests. Docker-backed PostgreSQL ran successfully.
- No commit, push, reset, checkout, cache deletion, or build-result deletion was performed. Remaining product scope is unchanged and no next feature was implemented.

## 2026-10-07 - MaintenanceRequest creation contract review

- Reviewed the create slice against ADR-019, Frozen API/permission/ERD, AGENTS.md, and the current tests without changing Java or Flyway code.
- Finding: `MaintenanceRequestExceptionHandler.code()` maps `BuildingNotFoundException` and `DormitoryNotFoundException` to the fallback `SPACE_NOT_FOUND`; the existing structure contract uses `BUILDING_NOT_FOUND` and `DORMITORY_NOT_FOUND`. This is a Frozen API error-code mismatch and should be fixed before commit.
- Finding: the current tests do not cover the missing-building/missing-dormitory error codes, invalid preferred-time pairs, inactive hierarchy/category, malformed/unknown fields, missing category/facility references, or rollback when the RequestHistory insert fails. The successful test verifies one history row but does not prove rollback atomicity.
- Review notes: the application command is one `@Transactional` service operation; the request and one `REQUEST_CREATED` history save share that transaction; no history delete/update API or entity mutator exists; the controller does not access repositories or own transactions; V6 is Flyway-managed with the expected FK/check/index constraints. The pending private request number is replaced with `DF-<id>` before transaction commit, but it causes two flushes and temporarily stores a non-public placeholder, so this remains a design risk to document or cover.

## 2026-10-07 - MaintenanceRequest creation review fixes and quality gate

- Mapped `BuildingNotFoundException` and `DormitoryNotFoundException` to the frozen `BUILDING_NOT_FOUND` and `DORMITORY_NOT_FOUND` error codes.
- Added regression coverage for missing category/facility (404), inactive hierarchy/category (409), invalid preferred visit times and unknown JSON fields (400), and the representative building/dormitory handler mappings.
- Added a PostgreSQL integration regression proving a `RequestHistory` save failure rolls back the newly created `MaintenanceRequest` in the same transaction. The endpoint returns an error and neither operational row nor history row remains.
- Documented why the temporary `P-...` request number is transaction-private while the IDENTITY key is allocated, and expanded the successful-create assertion to prove the API exposes only the final `DF-<id>` value.
- Full quality gate passed in order: `gradlew --stop`, repository guard, `git diff --check`, `check bootJar --no-daemon --console=plain`, and `integrationTest --no-daemon --console=plain`.
- Final integrationTest aggregation: 15 suites, 47 tests, 0 failures, 0 errors, 0 skipped. No cache/build output was deleted; no commit or push was performed.
