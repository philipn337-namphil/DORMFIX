# Infrastructure scope

Read `docs/architecture/deployment.md` and operations runbooks before changing templates. Local Compose and production Compose have different credential/network/migration responsibilities. Never copy local PostgreSQL owner credentials into production.

Templates are not permission to deploy or create AWS resources. Keep production image references immutable, application ports loopback-only, RDS private, runtime non-root, and secrets outside Git. Validate Compose with placeholder environment values; validate Nginx on the target before reload. Never use `down -v` as ordinary recovery. Document migration compatibility and rollback impact for every deployment change.
