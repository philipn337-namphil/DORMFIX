# Frozen V1 권한과 state machine

Authorization은 Authentication → Role → Ownership/Assignment/Scope → Current State 순으로 평가한다. 인증된 잘못된 role, non-owner, former assignee는 403이고, authorized actor의 state/version conflict는 409다. SUPER_ADMIN도 lifecycle rule을 우회하지 않는다. 다중 role 조합과 dormitory-scope 저장 세부사항은 review 대상이다.

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

- RESIDENT: 본인 request 생성/조회, REPORTED에서만 수정, 본인 active request에 PUBLIC comment와 허용 attachment 추가, visit schedule 조회, RESOLVED request close/reopen. status, priority, reporter, assignment를 직접 지정하지 않는다. STAFF_ONLY comment는 보지 못한다.
- WORKER: current active assignee만 start/hold/resume/resolve, valid state의 visit 관리, WorkLog, PUBLIC/STAFF_ONLY comment, repair attachment를 작성한다. Former worker는 historical read-only만 가능하다.
- ADMIN: dormitory scope의 전체 request 조회, active request의 허용된 category/priority/location 조정, assign/reassign, REPORTED reject/duplicate, visit 관리, RESOLVED close/reopen, facility/category/residence 관리. Resident entry consent를 대신 변경하지 않는다.
- SUPER_ADMIN: ADMIN 기능과 Dormitory/Building/Space, user-role, system/master-data 관리를 가진다. 모든 domain state rule은 동일하게 적용된다.

## Visit과 보존

VisitStatus는 SCHEDULED, COMPLETED, CANCELED, NO_ACCESS다. Request가 ASSIGNED, IN_PROGRESS, ON_HOLD일 때만 생성한다. Visit 완료는 request resolve가 아니다. 생성 시 `entry_policy`를 `entry_policy_snapshot`으로, `contact_before_entry`를 visit에 snapshot하여 이후 request 변경에도 immutable하게 보존한다. EntryPolicy는 RESIDENT_PRESENT_REQUIRED 또는 ABSENT_ENTRY_ALLOWED이며 contact_before_entry는 독립적이다.

User, Dormitory, Building, Space, Facility, MaintenanceCategory, MaintenanceRequest, MaintenanceAssignment, MaintenanceVisit, WorkLog, RequestHistory는 임의 hard-delete하지 않는다. Residence history를 보존하고 Comment는 soft-delete, WorkLog/RequestHistory는 append-only다. Attachment 삭제는 S3 cleanup policy와 함께 처리한다.
