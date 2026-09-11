# Rollback and recovery

Before deployment record current and previous immutable image digests, schema version, migration compatibility, backups/PITR status and restore rehearsal evidence. Retain the previous image in the registry. Expand/contract changes should allow the previous app to read/write the new schema.

If a new app fails readiness/smoke: stop promotion, inspect safe logs and DB/migration state, select the retained compatible digest in the operator release file, pull it and recreate the application service with the same configuration. Verify localhost readiness and end-to-end HTTPS smoke. Do not use docker compose down -v, mutable latest or rebuild old source as rollback.

If the schema is incompatible, do not blindly start an old image. Prefer a reviewed forward fix. Restore/PITR is a deliberate incident decision with downtime/data-loss analysis and explicit human authorization, ideally into a new database for verification before cutover. Never automatically reverse a migration or overwrite newer writes. Flyway repair is not a normal recovery tool.

If migration failed, inspect transactional/nontransactional effects, lock timeouts and checksum history before choosing a fix. Keep app stopped if its required schema is not present. If S3 or notifications fail, distinguish already committed request/history from side-effect failure; do not repeat lifecycle commands blindly. V1 internal events can be lost and require reviewed reconciliation, not a claim of automatic replay.

Document incident timeline, root cause and regression test/runbook improvement after recovery. Restore credentials and network access remain outside the repository.
