"""Small, offline repository guard. Complements tests and human review; not a security scanner."""

import hashlib
import json
import re
import sys
from pathlib import Path
from urllib.parse import unquote

ROOT = Path(__file__).resolve().parents[1]
IGNORED = {".git", ".tools", ".gradle", "build", "__pycache__"}
REQUIRED = (
    "AGENTS.md", "README.md", "backend/AGENTS.md", "docs/AGENTS.md", "infra/AGENTS.md",
    "backend/build.gradle", "backend/settings.gradle", "backend/gradlew", "backend/gradlew.bat",
    "backend/gradle/wrapper/gradle-wrapper.jar", "backend/gradle/wrapper/gradle-wrapper.properties",
    "backend/src/main/resources/application.yml", "backend/src/main/resources/application-prod.yml",
    "backend/src/main/resources/db/migration/V1__foundation.sql", "backend/Dockerfile",
    "compose.yml", ".env.example", ".github/workflows/ci.yml", ".github/pull_request_template.md",
    "docs/README.md", "docs/architecture/open-questions.md", "docs/adr/README.md",
    "docs/development/roadmap.md", "docs/development/definition-of-done.md",
    "docs/development/testing-strategy.md", "docs/development/foundation-validation.md",
    "infra/production/compose.yml", "infra/production/app.env.example",
    "infra/nginx/dormfix.conf.template", "infra/systemd/dormfix.service",
)
FROZEN = (
    "docs/product/problem-definition.md", "docs/product/v1-scope.md", "docs/product/erd-v1.md",
    "docs/product/api-spec-v1.md", "docs/product/permission-state-matrix-v1.md",
    "docs/architecture/aggregate-boundaries.md", "docs/architecture/transaction-boundaries.md",
    "docs/architecture/domain-events.md",
)
WRAPPER_SHA256 = "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172"
DISTRIBUTION_SHA256 = "bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531"


def text_hash(path):
    # Git may check text out as CRLF on Windows. Hash canonical LF, not OS line endings.
    return hashlib.sha256(path.read_text(encoding="utf-8").encode("utf-8")).hexdigest()


def check(root=ROOT):
    errors = []
    for name in REQUIRED:
        if not (root / name).is_file():
            errors.append(f"Missing required file: {name}")

    manifest = root / "docs/product/frozen-baseline.sha256.json"
    try:
        expected = json.loads(manifest.read_text(encoding="utf-8"))
        if set(expected) != set(FROZEN):
            errors.append("Frozen manifest must cover exactly the authoritative contract files")
        for name in FROZEN:
            path = root / name
            if not path.is_file() or text_hash(path) != expected.get(name):
                errors.append(f"Frozen baseline changed: {name}; approved ADR and review required")
    except (OSError, ValueError) as error:
        errors.append(f"Cannot read frozen manifest: {error}")

    for path in root.rglob("*.md"):
        if any(part in IGNORED for part in path.relative_to(root).parts) or "context" in path.parts:
            continue
        content = path.read_text(encoding="utf-8")
        for target in re.findall(r"\[[^\]]*\]\(([^)]+)\)", content):
            if target.startswith(("https://", "http://", "mailto:", "#")):
                continue
            target = unquote(target.strip("<>").split("#", 1)[0])
            if not (path.parent / target).exists():
                errors.append(f"Broken relative link in {path.relative_to(root)}: {target}")
        if re.search(r"^(<{7}|={7}|>{7})( |$)", content, re.MULTILINE):
            errors.append(f"Unresolved merge marker: {path.relative_to(root)}")

    wrapper = root / "backend/gradle/wrapper/gradle-wrapper.jar"
    if wrapper.is_file() and hashlib.sha256(wrapper.read_bytes()).hexdigest() != WRAPPER_SHA256:
        errors.append("Gradle wrapper JAR differs from the verified Gradle 8.14.3 publisher checksum")
    properties = root / "backend/gradle/wrapper/gradle-wrapper.properties"
    if properties.is_file():
        content = properties.read_text(encoding="utf-8")
        if f"distributionSha256Sum={DISTRIBUTION_SHA256}" not in content:
            errors.append("Gradle distribution checksum missing or changed; review the wrapper upgrade")
        if "gradle-8.14.3-bin.zip" not in content or "validateDistributionUrl=true" not in content:
            errors.append("Gradle wrapper version/URL validation differs from baseline")

    resources = root / "backend/src/main/resources"
    for path in resources.glob("application*.yml"):
        content = path.read_text(encoding="utf-8")
        for pattern in (r"ddl-auto:\s*(update|create|create-drop)\b", r"open-in-view:\s*true\b",
                        r"clean-disabled:\s*false\b", r"baseline-on-migrate:\s*true\b"):
            if re.search(pattern, content):
                errors.append(f"Unsafe schema/runtime setting in {path.relative_to(root)}")
    application = resources / "application.yml"
    if application.is_file():
        content = application.read_text(encoding="utf-8")
        for required in ("ddl-auto: validate", "open-in-view: false", "clean-disabled: true",
                         "validate-on-migrate: true", "show-details: never"):
            if required not in content:
                errors.append(f"Required application control missing: {required}")

    workflow = root / ".github/workflows/ci.yml"
    if workflow.is_file():
        content = workflow.read_text(encoding="utf-8")
        for required in ("scripts/check_repository.py", "check bootJar integrationTest",
                         "docker compose config --quiet", "contents: read"):
            if required not in content:
                errors.append(f"CI gate missing: {required}")
        if "pull_request_target:" in content:
            errors.append("Privileged PR execution requires an explicit security review")

    for path in (root / "backend/src").rglob("*.java"):
        content = path.read_text(encoding="utf-8")
        if re.search(r"disabledWithoutDocker\s*=\s*true|@Disabled\b", content):
            errors.append(f"Disabled test requires review, not a silent bypass: {path.relative_to(root)}")
    return errors


if __name__ == "__main__":
    problems = check()
    for problem in problems:
        print(f"FAIL: {problem}")
    if problems:
        sys.exit(1)
    print("Repository controls passed: files, contract hashes, links, wrapper, configuration and CI gates.")
