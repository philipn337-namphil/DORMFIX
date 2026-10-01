# Database와 JPA 규칙

PostgreSQL 17 계열을 local/Testcontainers 기준으로 사용하고 `postgres:17.6-alpine`을 재현성 있게 고정한다. Flyway만 schema authority이며 Hibernate는 `ddl-auto=validate`, 모든 profile은 `open-in-view=false`다. Phase 0의 `V1__foundation.sql`은 의도적으로 `SELECT 1`만 수행하며 business DDL은 각 feature slice에서 검토한 migration으로 추가한다. H2로 대체하지 않는다.

물리 매핑은 snake_case, `User`는 `app_user`, BIGINT identity와 `GenerationType.IDENTITY`를 사용한다. Frozen logical ERD field를 바꾸지 않는다. Enum은 `@Enumerated(STRING)`과 명명된 CHECK를 사용하고 ordinal을 금지한다. `Instant`는 TIMESTAMPTZ, `LocalDate`는 Residence/설치일, 시간은 UTC와 ISO-8601 offset을 사용한다. auditing timestamp는 test 가능한 Clock과 immutable `created_at`을 보존한다.

Residence is a DATE-range history table. Flyway enables PostgreSQL `btree_gist` and names the
`resident_id` / `daterange(start_date, end_date, '[)')` exclusion constraint so application code
can map its violation to the stable `RESIDENCE_PERIOD_OVERLAP` conflict. Application checks remain
for readable validation; the database constraint is the concurrency backstop.

Dormitory structure V1 physical rules: Dormitory의 `name`, `address`, `timezone`은 NOT NULL이며 timezone DB default는 두지 않는다. Dormitory `name`에는 V1 unique constraint를 두지 않는다. Building·Space·Facility·MaintenanceCategory의 ERD 식별·필수 필드는 NOT NULL로 두고, ERD에서 nullable인 필드는 NULL을 허용한다. `active`는 NOT NULL DEFAULT TRUE, Facility `status`는 NOT NULL DEFAULT `ACTIVE`, `created_at`·`updated_at`은 NOT NULL DEFAULT CURRENT_TIMESTAMP로 둔다. `updated_at` 자동 갱신 trigger는 사용하지 않는다. Building→Dormitory, Space→Building, Facility→Space, MaintenanceCategory→MaintenanceCategory FK는 모두 ON DELETE RESTRICT를 명시한다. Category `parent_id`는 nullable이고 자기 자신 참조만 CHECK로 차단하며 전체 cycle 검사는 V1 migration에서 구현하지 않는다. 상위 비활성화는 하위로 전파하지 않고, 비활성 상위 아래 생성·재활성화 차단은 application rule이다.

Entity equality는 mutable field/association이 아닌 안정적인 non-null ID 기준으로만 검토하고, value object는 value equality를 사용한다. Aggregate 간에는 scalar ID를 우선하며 association은 명시적 LAZY·기본 unidirectional로 둔다. uncontrolled `CascadeType.ALL`, orphanRemoval, broad bidirectional graph를 만들지 않는다. API에는 DTO/projection만 반환하고 N+1은 fetch join/entity graph/projection으로 측정·해결한다. Pagination은 bounded page size와 stable sort를 사용한다.

MaintenanceRequest는 `@Version Long version`을 사용하고 supplied version과 flush race를 409 `VERSION_CONFLICT`로 처리한다. Active assignment는 `unassigned_at IS NULL` partial unique index로 보호한다. 모든 production schema 변경은 immutable `V<number>__description.sql`이며 business migration은 V2부터 시작한다. 적용 migration을 수정하거나 `baseline-on-migrate`, `repair`, shared DB `clean`, routine down migration을 사용하지 않는다. DDL identity와 runtime DML identity를 분리한다.

위험한 변경은 expand/contract, bounded backfill, timeout, compatibility check, backup 후 수행한다. 운영/history data를 casual hard-delete하지 않고 append-only 저장소에 update/delete API를 만들지 않는다.
