# Repository controls

Run `python scripts/check_repository.py` and `python -m unittest discover -s scripts -p 'test_*.py'` from the repository root. Only Python's standard library is needed. Guard regression tests use isolated temporary copies inside ignored `.tools/` and remove those copies on completion.

The guard detects broken local Markdown links, missing core files, accidental frozen-contract edits, wrapper tampering and selected unsafe configuration/CI changes. It is deliberately small; it does not replace YAML parsing, Gradle/ArchUnit/Testcontainers, a secret scanner or human review. Historical `docs/context/` is excluded from link checking, not changed or deleted.

`docs/product/frozen-baseline.sha256.json` hashes canonical UTF-8/LF text, so Windows line-ending conversion does not cause false drift. It covers all five frozen product documents and the aggregate/transaction/event contracts. There is no automatic baseline-update command: approved changes require a reviewed ADR, explicit manifest update and synchronized harness/docs/tests. The manifest and checker can themselves be edited, so PR review remains the authority.

The Gradle 8.14.3 wrapper/distribution hashes are pinned against the publisher's checksum endpoints under `https://services.gradle.org/distributions/`. Tool upgrades must update both the wrapper and reviewed checker constants.
