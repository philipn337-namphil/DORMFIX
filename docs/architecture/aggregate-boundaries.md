# Frozen aggregate boundary

MaintenanceRequest는 `assign()`, `start()`, `hold()`, `resume()`, `resolve()`, `close()`, `reopen()`, `reject()`, `markDuplicate()` 명령으로 lifecycle을 독점한다. SUPER_ADMIN도 invariant를 우회하지 않는다.

MaintenanceAssignment, MaintenanceVisit, Attachment, Comment, WorkLog, RequestHistory, Notification은 각각 별도 Aggregate/Repository다. User, Dormitory, Building, Space, Residence, Facility, MaintenanceCategory도 독립적으로 유지한다. Request entity에 모든 collection을 넣거나 broad bidirectional graph를 만들지 않고 aggregate 간에는 scalar ID와 명시적 query/projection을 사용한다.

Application Service가 cross-aggregate 변경을 하나의 local transaction으로 조정하고, Domain은 local invariant, Application은 authorization/orchestration, DB는 race-sensitive invariant를 담당한다. WorkLog/RequestHistory는 append-only이며 Visit 완료는 Request resolve가 아니다. S3 metadata 등록은 별도 authorized action이다.
