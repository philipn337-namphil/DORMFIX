"""Regression tests for guard failures; use isolated repository copies, never edit the baseline."""

import json
import shutil
import tempfile
import unittest
from pathlib import Path

from check_repository import FROZEN, REQUIRED, ROOT, check


class RepositoryGuardTest(unittest.TestCase):
    def setUp(self):
        scratch = ROOT / ".tools"
        scratch.mkdir(exist_ok=True)
        self.temporary = tempfile.TemporaryDirectory(prefix="guard-test-", dir=scratch)
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        names = set(REQUIRED) | set(FROZEN) | {"docs/product/frozen-baseline.sha256.json"}
        names.update(str(p.relative_to(ROOT)) for p in (ROOT / "docs").rglob("*.md")
                     if "context" not in p.parts)
        for name in names:
            source = ROOT / name
            target = self.root / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(source, target)

    def test_baseline_passes(self):
        self.assertEqual([], check(self.root))

    def test_frozen_change_is_rejected(self):
        path = self.root / "docs/product/erd-v1.md"
        path.write_text(path.read_text(encoding="utf-8") + "\nUnapproved column\n", encoding="utf-8")
        self.assertTrue(any("Frozen baseline changed" in error for error in check(self.root)))

    def test_deleting_manifest_entry_does_not_bypass_guard(self):
        path = self.root / "docs/product/frozen-baseline.sha256.json"
        content = json.loads(path.read_text(encoding="utf-8"))
        del content[FROZEN[0]]
        path.write_text(json.dumps(content), encoding="utf-8")
        self.assertTrue(any("exactly" in error for error in check(self.root)))

    def test_broken_link_is_rejected(self):
        (self.root / "README.md").write_text("[missing](absent.md)\n", encoding="utf-8")
        self.assertTrue(any("Broken relative link" in error for error in check(self.root)))

    def test_unsafe_schema_setting_is_rejected(self):
        path = self.root / "backend/src/main/resources/application.yml"
        path.write_text(path.read_text(encoding="utf-8").replace("ddl-auto: validate", "ddl-auto: update"),
                        encoding="utf-8")
        self.assertTrue(any("Unsafe schema" in error for error in check(self.root)))

    def test_integration_gate_removal_is_rejected(self):
        path = self.root / ".github/workflows/ci.yml"
        path.write_text(path.read_text(encoding="utf-8").replace("check bootJar integrationTest", "check bootJar"),
                        encoding="utf-8")
        self.assertTrue(any("CI gate missing" in error for error in check(self.root)))

    def test_wrapper_tampering_is_rejected(self):
        (self.root / "backend/gradle/wrapper/gradle-wrapper.jar").write_bytes(b"not the wrapper")
        self.assertTrue(any("wrapper JAR differs" in error for error in check(self.root)))


if __name__ == "__main__":
    unittest.main()
