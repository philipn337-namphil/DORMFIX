# Infrastructure templates

Root compose.yml is runnable local PostgreSQL + app after .env is populated. backend/Dockerfile builds and unit/architecture-checks the Java application. PostgreSQL integration tests run outside the image build in CI with Docker available.

production/compose.yml, nginx/ and systemd/ are deployment templates, not a completed or authorized deployment. Production requires a retained immutable image digest, real RDS/TLS/secret configuration, a reviewed migration step and target-host validation. See [deployment architecture](../docs/architecture/deployment.md) and [Ubuntu runbook](../docs/operations/ubuntu-deployment.md). No AWS resources, credentials, Terraform state or deployment automation is included.
