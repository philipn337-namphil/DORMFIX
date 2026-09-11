# Frozen transactions and concurrency

Controllers never own transactions. A command normally executes within one application-service @Transactional method; read/query use cases use readOnly=true where appropriate. Avoid self-invocation that bypasses Spring proxies. Load only the aggregates required by the use case.

Assign: load request -> validate submitted version -> validate scoped administrator/worker -> close previous assignment if reassigning -> create new assignment -> request.assign() -> append RequestHistory -> publish value event within transaction -> commit -> AFTER_COMMIT reaction. Reassignment from ASSIGNED/IN_PROGRESS/ON_HOLD returns ASSIGNED; the new worker must start again. All DB changes roll back together on failure.

Resolve: load request -> verify current active assignment and worker -> compare version -> validate IN_PROGRESS -> request.resolve(), resolved_at -> RequestHistory -> event -> commit -> effects.

Close: load request -> validate reporter or scoped admin -> compare version -> validate RESOLVED -> request.close(), closed_at -> history -> event -> commit -> effects. Terminal states remain terminal.

These sequences describe required checks, not permission to expose sensitive resource details before authentication. API review tests the frozen distinction: 401 missing/invalid auth, 403 authenticated wrong role/relationship, 404 nonexistent, 409 authorized state/concurrency conflict, 400 malformed input.

Required submitted versions: update, assign/reassign, start, hold, resume, resolve, close, reopen, reject, mark duplicate. Compare the caller's version; JPA @Version also protects the interval until flush/commit. Never implement manual version increments or bulk updates that evade the version predicate. Let optimistic-lock exceptions propagate through the transaction boundary and translate to 409 VERSION_CONFLICT. Do not catch and continue within a rollback-only transaction. Assignment index violations map to the relevant assignment conflict, not every DB error to VERSION_CONFLICT.

Concurrent assignment tests must demonstrate exactly one active assignment and one winning command history. PostgreSQL partial unique index protects unassigned_at IS NULL; lock/update the request as part of the same use case. Do not introduce pessimistic locking by default.

Never hold a core DB transaction across S3, email, SQS or long network activity. Presign authorization/read -> release DB transaction -> issue URL -> client upload -> register metadata separately after verification. External effects occur only after successful commit. Side-effect failures cannot roll back already committed business state. See [event handling](domain-events.md).
