"""Executable safety/evidence tests for WOLF AI 6.0 agent loop."""
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from wolf_ai.agent_engine import (
    AgentError,
    Plan,
    classify,
    diagnoses,
    execute,
    inventory,
    make_plan,
)


def audit(status="passed", step_status="passed"):
    return {"status": status, "steps": [
        {"name": "python-syntax", "status": step_status,
         "log": "Traceback: SyntaxError" if step_status == "failed" else "OK"},
    ]}


class AgentEngineTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        root = Path(self.tmp.name)
        self.source = root / "repo"
        self.source.mkdir()
        (self.source / "wolf_ai").mkdir()
        (self.source / "wolf_ai" / "sample.py").write_text("x=1\n", encoding="utf-8")
        (self.source / ".git").mkdir()
        self.output = root / "reports" / "agent.json"

    def test_deterministic_allowlist_and_no_goal_execution(self):
        p = make_plan("wolf", "test",
                      "Проверь все ошибки; touch /tmp/pwned; rm -rf /", safe_fix=False)
        self.assertEqual(p.project, "wolf")
        self.assertNotIn("touch", p.steps)
        self.assertIn("execute_allowlisted_checks", p.steps)
        self.assertEqual(len(p.goal_sha256), 64)
        self.assertFalse(p.safe_fix)
        for bad in ("../secret", "trade", "arbitrary-repo"):
            with self.assertRaises(AgentError):
                make_plan(bad, "test", "Проверка безопасности")
        with self.assertRaises(AgentError):
            make_plan("studio", "publish", "Опубликуй ролик")
        with self.assertRaises(AgentError):
            make_plan("williams", "test", "Проверь торгового бота", safe_fix=True)
        with self.assertRaises(AgentError):
            make_plan("wolf", "test", "password = X123ABC")
        with self.assertRaises(AgentError):
            make_plan("wolf", "test", "X" * 601)

    def test_inventory_does_not_read_file_contents_or_follow_symlink(self):
        (self.source / "wolf_ai" / "other.py").write_text("password=super-secret\n")
        (self.source / "wolf_ai" / "leak.py").symlink_to(
            self.source / "wolf_ai" / "other.py")
        result = inventory(self.source)
        self.assertEqual(result["extension_counts"][".py"], 2)
        self.assertEqual(result["files_scanned"], 2)
        self.assertNotIn("super-secret", json.dumps(result))

    def test_classification_is_evidence_based(self):
        self.assertEqual(classify("ModuleNotFoundError: no module", "failed")[0], "dependency")
        self.assertEqual(classify("SyntaxError: invalid", "failed")[0], "syntax")
        self.assertEqual(classify("", "timeout")[0], "timeout")
        self.assertEqual(len(diagnoses({"steps": [audit("incomplete", "failed")["steps"][0]]})), 1)

    @patch("wolf_ai.agent_engine._audit", return_value=audit())
    def test_validates_real_check_status_not_a_fake_success(self, run_mock):
        plan = make_plan("wolf", "test", "Найди ошибки приложения")
        result = execute(plan, self.source, self.output, budget_seconds=120)
        self.assertEqual(result["status"], "verified_checks")
        self.assertEqual(result["verification"], "passed")
        self.assertEqual(run_mock.call_count, 1)
        self.assertFalse(result["social_posting"])
        self.assertFalse(result["source_sent_to_external_llm"])
        self.assertEqual(json.loads(self.output.read_text())["status"], "verified_checks")

    @patch("wolf_ai.agent_engine._audit", return_value=audit("incomplete", "failed"))
    def test_logical_failure_is_never_silently_retried(self, run_mock):
        plan = make_plan("wolf", "test", "Проверь код")
        result = execute(plan, self.source, self.output, budget_seconds=120)
        self.assertEqual(result["status"], "needs_attention")
        self.assertEqual(result["diagnoses"][0]["kind"], "syntax")
        self.assertEqual(run_mock.call_count, 1)
        self.assertEqual(result["verification"], "failed")

    @patch("wolf_ai.agent_engine._audit", side_effect=[
        audit("incomplete", "timeout"), audit("passed", "passed")])
    def test_transient_failure_gets_at_most_one_readonly_recovery(self, run_mock):
        plan = make_plan("wolf", "audit", "Проверь зависание")
        result = execute(plan, self.source, self.output, budget_seconds=180)
        self.assertEqual(result["status"], "verified_checks")
        self.assertEqual(run_mock.call_count, 2)
        self.assertEqual(result["retry_status"], "passed")
        self.assertEqual([x["stage"] for x in result["events"]].count(
            "recover_transient_checks"), 1)

    def test_source_and_report_boundaries_and_mutation_approval(self):
        plan = make_plan("wolf", "test", "Исправь Python", safe_fix=True)
        with self.assertRaises(AgentError):
            execute(plan, self.source, self.output, allow_safe_fix=False)
        with self.assertRaises(AgentError):
            execute(plan, self.source, self.source / "out.json",
                    allow_safe_fix=True)
        altered = Plan("wolf", "test", plan.goal_sha256,
                       ("arbitrary-shell-command",), True)
        with self.assertRaises(AgentError):
            execute(altered, self.source, self.output, allow_safe_fix=True)

    @patch("wolf_ai.agent_engine._audit", side_effect=RuntimeError("private thing"))
    def test_blocked_stage_checkpoints_without_secret_dump(self, audit_mock):
        plan = make_plan("studio", "audit", "Проверь студию")
        result = execute(plan, self.source, self.output, budget_seconds=180)
        self.assertEqual(result["status"], "blocked")
        self.assertEqual(result["error_kind"], "RuntimeError")
        self.assertNotIn("private thing", self.output.read_text())
        self.assertGreaterEqual(len(result["events"]), 2)


if __name__ == "__main__":
    unittest.main()
