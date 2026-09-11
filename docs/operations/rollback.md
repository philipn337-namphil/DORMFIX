# Rollback과 recovery

배포 전 current/previous immutable image digest, schema version, migration compatibility, backup/PITR, restore rehearsal evidence를 기록한다. Previous image는 registry에 보존한다. Expand/contract로 이전 app이 새 schema를 읽고 쓸 수 있게 한다.

새 app이 readiness/smoke에서 실패하면 promotion을 멈추고 safe log와 DB/migration state를 확인한 뒤 호환되는 retained digest를 pull해 같은 configuration으로 application service를 재생성한다. localhost readiness와 end-to-end HTTPS smoke를 검증한다. `docker compose down -v`, mutable `latest`, old source rebuild를 rollback에 사용하지 않는다.

Schema가 호환되지 않으면 old image를 맹목적으로 시작하지 않는다. Reviewed forward fix를 우선하고 restore/PITR은 downtime/data-loss 분석과 human authorization이 필요한 incident 결정이다. Migration을 자동 reverse하거나 최신 write를 덮어쓰지 않는다. S3/notification failure는 이미 commit된 request/history와 side effect failure를 구분한다.
