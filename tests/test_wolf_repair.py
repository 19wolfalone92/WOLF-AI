"""Deterministic security contracts for auto-repair. Works offline."""
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from wolf_ai.repair_engine import RepairError, is_repairable, run_repair, validate_diff
from wolf_ai.repair_engine import command as actual_command


class RepairEngineTests(unittest.TestCase):
    def test_allowlist_disallows_secrets_and_workflows(self):
        for good in ["wolf_ai/orchestrator.py", "tests/test_wolf_patch.py"]:
            self.assertTrue(is_repairable(good))
        for bad in ["README.md", ".github/workflows/a.yml", "wolf_ai/../private.py",
                    "secrets/token.py", "tests/__pycache__/x.py", "../wolf_ai/x.py",
                    "/tmp/wolf_ai/x.py", "android/app/build.gradle"]:
            self.assertFalse(is_repairable(bad), bad)

    def _repo(self, root: Path):
        repo = root / "repo"
        repo.mkdir()
        subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
        subprocess.run(["git", "config", "user.name", "Wolf Test"], cwd=repo, check=True)
        subprocess.run(["git", "config", "user.email", "wolf@example.invalid"], cwd=repo, check=True)
        (repo / "wolf_ai").mkdir()
        (repo / "tests").mkdir()
        (repo / "wolf_ai" / "module.py").write_text("x = 1\n", encoding="utf-8")
        (repo / "tests" / "test_wolf_simple.py").write_text(
            "import unittest\nclass T(unittest.TestCase):\n def test_ok(self): self.assertTrue(True)\n",
            encoding="utf-8")
        subprocess.run(["git", "add", "."], cwd=repo, check=True)
        subprocess.run(["git", "commit", "-qm", "test fixture"], cwd=repo, check=True)
        return repo

    def test_plan_only_does_not_modify(self):
        with tempfile.TemporaryDirectory() as td:
            repo = self._repo(Path(td))
            outcome = run_repair(repo, apply=False, output=Path(td) / "report.json")
            self.assertEqual(outcome["status"], "planned")
            self.assertEqual((repo / "wolf_ai" / "module.py").read_text(), "x = 1\n")
            self.assertEqual(outcome["tests"], "not_run")
            self.assertEqual(json.loads((Path(td) / "report.json").read_text())["status"], "planned")

    def test_refuses_dirty_and_source_relative_report(self):
        with tempfile.TemporaryDirectory() as td:
            repo = self._repo(Path(td))
            (repo / "new.txt").write_text("dirty\n")
            with self.assertRaises(RepairError):
                run_repair(repo, apply=True, output=Path(td) / "report.json")
            (repo / "new.txt").unlink()
            with self.assertRaises(RepairError):
                run_repair(repo, apply=True, output=repo / "wolf_ai" / "report.json")

    def test_rejects_changes_outside_allowlist(self):
        with tempfile.TemporaryDirectory() as td:
            repo = self._repo(Path(td))
            (repo / "README.md").write_text("original\n")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(["git", "commit", "-qm", "readme"], cwd=repo, check=True)
            (repo / "wolf_ai" / "module.py").write_text("x = 2\n")
            (repo / "README.md").write_text("changed\n")
            with self.assertRaises(RepairError):
                validate_diff(repo)

    def test_mocked_safe_autofix_versus_test_fail(self):
        with tempfile.TemporaryDirectory() as td:
            repo = self._repo(Path(td))
            def fake_command(root, argv, timeout=180):
                if argv[0] == "git":
                    return actual_command(root, argv, timeout)
                if argv[:2] == ["ruff", "check"] and "--fix" in argv:
                    (repo / "wolf_ai" / "module.py").write_text("x = 2\n")
                return subprocess.CompletedProcess(argv, 0, "OK\n")

            with patch("wolf_ai.repair_engine.command", side_effect=fake_command):
                result = run_repair(repo, apply=True, output=Path(td) / "report.json")
            self.assertEqual(result["status"], "verified_change")
            self.assertEqual(result["modified_files"], ["wolf_ai/module.py"])
            self.assertEqual(result["tests"], "passed")
            self.assertTrue(result["applied"])
            self.assertFalse(result["auto_merged"])

    def test_prohibits_binary_and_unsafe_paths(self):
        self.assertFalse(is_repairable("wolf_ai/.env.py"))
        with tempfile.TemporaryDirectory() as td:
            repo = self._repo(Path(td))
            (repo / "wolf_ai" / "module.py").write_text("x = 3\n")
            files, patch_text = validate_diff(repo)
            self.assertEqual(files, ["wolf_ai/module.py"])
            self.assertIn("diff --git", patch_text)


if __name__ == "__main__":
    unittest.main()
