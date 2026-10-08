# Frozen V1 권한과 state machine

Authorization은 Authentication → Role → Ownership/Assignment/Scope → Current State 순으로 평가한다. 인증된 잘못된 role, non-owner, former assignee는 403이고, authorized actor의 state/version conflict는 409다. SUPER_ADMIN도 lifecycle rule을 우회하지 않는다. ADMIN은 명시된 dormitory scope 안에서만 관리 권한을 가진다.

| Command | Source | Target | Authorized relationship |
|---|---|---|---|
| assign | REPORTED | ASSIGNED | ADMIN/SUPER_ADMIN within dormitory scope |
| reassign | ASSIGNED, IN_PROGRESS, ON_HOLD | ASSIGNED | ADMIN/SUPER_ADMIN within scope |
| start | ASSIGNED | IN_PROGRESS | current active worker |
| hold | IN_PROGRESS | ON_HOLD | current active worker |
| resume | ON_HOLD | IN_PROGRESS | current active worker |
| resolve | IN_PROGRESS | RESOLVED | current active worker |
| close | RESOLVED | CLOSED | reporter RESIDENT or scoped ADMIN/SUPER_ADMIN |
| reopen | RESOLVED | IN_PROGRESS | reporter RESIDENT or scoped ADMIN/SUPER_ADMIN |
| reject | REPORTED | REJECTED | scoped ADMIN/SUPER_ADMIN |
| mark duplicate | REPORTED | DUPLICATE | scoped ADMIN/SUPER_ADMIN; reference original |

CLOSED, REJECTED, DUPLICATE는 terminal이다. 다른 transition은 허용하지 않는다. CLOSED request는 reopen할 수 없다. Reassignment는 기존 assignment를 종료하고 새 assignment를 만들며 새 worker가 다시 start한다. Request당 `unassigned_at IS NULL` assignment는 최대 하나이고 PostgreSQL partial unique index로 보강한다.

## Role별 규칙

- RESIDENT: 본인 request 생성/조회, REPORTED에서만 수정, 본인 active request에 PUBLIC comment와 허용 attachment 추가, visit schedule 조회, RESOLVED request close/reopen. 신고 생성은 본인의 current Residence ROOM에만 가능하며 공용공간이나 다른 resident의 ROOM은 신고하지 않는다. `OUT_OF_SERVICE` Facility는 신고할 수 있지만 `RETIRED` Facility는 신고할 수 없다. status, priority, reporter, assignment를 직접 지정하지 않는다. STAFF_ONLY comment는 보지 못한다.
- WORKER: current active assignee만 start/hold/resume/resolve, valid state의 visit 관리, WorkLog, PUBLIC/STAFF_ONLY comment, repair attachment를 작성한다. Former worker는 historical read-only만 가능하다.
- ADMIN: dormitory scope의 전체 request 조회, active request의 허용된 category/priority/location 조정, assign/reassign, REPORTED reject/duplicate, visit 관리, RESOLVED close/reopen, scope 내 Facility 관리와 Facility status command 수행. Resident entry consent를 대신 변경하지 않으며 MaintenanceCategory를 관리하지 않는다.
- SUPER_ADMIN: ADMIN 기능과 전체 Dormitory/Building/Space/Facility, user-role 및 전역 system/master-data 관리를 가진다. MaintenanceCategory는 V1 전역 Master Data이므로 SUPER_ADMIN 전용이다. 모든 domain state rule은 동일하게 적용된다.

## Dormitory structure administration

- Dormitory, Building, Space의 생성·수정·비활성화는 `SUPER_ADMIN`만 수행할 수 있다.
- Facility의 생성·수정·비활성화는 해당 Dormitory scope 안의 `ADMIN` 또는 `SUPER_ADMIN`이 수행할 수 있다.
- MaintenanceCategory는 V1 전역 Master Data이며 생성·수정·비활성화 모두 `SUPER_ADMIN`만 수행할 수 있다.
- Facility 상태는 `ACTIVE → OUT_OF_SERVICE → ACTIVE`를 허용하고, `ACTIVE` 또는 `OUT_OF_SERVICE`에서 `RETIRED`로 전환할 수 있다. `RETIRED`는 terminal이며 재활성화할 수 없다. 상태 명령은 `/out-of-service`, `/reactivate`, `/retire`로 분리한다.
- MaintenanceCategory의 `parentId`는 생성 시에만 설정한다. 수정 DTO에는 포함하지 않으며, V1에는 category 이동 API가 없다.
- MaintenanceCategory reactivate는 SUPER_ADMIN만 수행하며, 비활성 parent 아래 reactivation은 409 `INACTIVE_PARENT`다. parent deactivate는 하위로 전파하지 않는다.
- Residence create/detail/end와 admin list는 room Dormitory scope의 ADMIN 또는 SUPER_ADMIN만 가능하다. Admin list는 scope 밖 결과를 반환하지 않고, 명시한 scope 밖 room filter는 403이다. RESIDENT는 자신의 paged history만 조회하며 WORKER-only와 scope 없는 ADMIN은 접근할 수 없다. 기간 겹침, same-day end, non-ROOM 또는 inactive hierarchy는 409이다.
- Dormitory `name`은 V1에서 unique가 아니며 중복 이름은 409 conflict가 아니다.
- 상위 비활성화는 하위로 자동 전파하지 않는다. 비활성 상위 아래의 생성과 재활성화는 허용하지 않는다.
- 운영 데이터는 hard delete하지 않는다.

## Visit과 보존

VisitStatus는 SCHEDULED, COMPLETED, CANCELED, NO_ACCESS다. Request가 ASSIGNED, IN_PROGRESS, ON_HOLD일 때만 생성한다. Visit 완료는 request resolve가 아니다. 생성 시 `entry_policy`를 `entry_policy_snapshot`으로, `contact_before_entry`를 visit에 snapshot하여 이후 request 변경에도 immutable하게 보존한다. EntryPolicy는 RESIDENT_PRESENT_REQUIRED 또는 ABSENT_ENTRY_ALLOWED이며 contact_before_entry는 독립적이다.

User, Dormitory, Building, Space, Facility, MaintenanceCategory, MaintenanceRequest, MaintenanceAssignment, MaintenanceVisit, WorkLog, RequestHistory는 임의 hard-delete하지 않는다. Residence history를 보존하고 Comment는 soft-delete, WorkLog/RequestHistory는 append-only다. Attachment 삭제는 S3 cleanup policy와 함께 처리한다.
