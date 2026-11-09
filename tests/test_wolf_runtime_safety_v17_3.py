"""Pure-stdlib integration contracts and Android security audit for WOLF 17.3."""
from __future__ import annotations

import json
from pathlib import Path
import unittest

from wolf_ai.dispatch_contract import schema, validate_dispatch
from wolf_ai.orchestrator import PROJECTS, recipes

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "android/app/src/main/java/ai/wolf/autopilot"


def java(name: str) -> str:
    return (ANDROID / f"{name}.java").read_text(encoding="utf-8")


class AgentProtocolTests(unittest.TestCase):
    def test_json_contract_matches_actual_jvm_and_python_enums(self):
        contract = schema()
        enum = contract["properties"]["project"]["enum"]
        self.assertEqual(set(enum), set(PROJECTS))
        parser = java("CommandParser")
        for project in enum:
            with self.subTest(project=project):
                self.assertIn(f'"{project}"', parser)
                for mode in contract["properties"]["mode"]["enum"]:
                    self.assertEqual(validate_dispatch({
                        "project": project, "mode": mode, "client_task_id": "manual"
                    })["mode"], mode)
                    self.assertTrue(recipes(project, mode))
        self.assertEqual(set(contract["properties"]["mode"]["enum"]), {"audit", "test"})
        self.assertEqual(contract["additionalProperties"], False)

    def test_invalid_contract_commands_fail_closed(self):
        valid = {"project": "wolf", "mode": "test",
                 "client_task_id": "w" + "a" * 32}
        for bad in [
            {"project": "stranger"}, {"mode": "publish"},
            {"client_task_id": "invalid"}, {"client_task_id": "w" + "A" * 32},
            {"extra_shell": "rm -rf /"},
            {"safe_fix": "true"},
            {"safe_fix": True, "mode": "audit"},
            {"goal": ""},
            {"goal": "X" * 601},
        ]:
            with self.subTest(bad=bad):
                with self.assertRaises(ValueError):
                    validate_dispatch({**valid, **bad})
        with self.assertRaises(ValueError):
            validate_dispatch({"project": "wolf"})
        self.assertTrue(validate_dispatch({**valid, "goal": "Проверь проект",
                                            "safe_fix": True}))

    def test_android_and_python_validate_at_runtime(self):
        self.assertIn("validate_dispatch(", (
            ROOT / "wolf_ai/agent_engine.py").read_text("utf-8"))
        self.assertIn("validate_dispatch(", (
            ROOT / "wolf_ai/orchestrator.py").read_text("utf-8"))
        self.assertIn("validTaskUid", java("CommandParser"))
        github = java("GithubApi")
        self.assertIn("CommandParser.validProject(project)", github)
        self.assertIn("client_task_id", github)

    def test_suspected_sensitive_text_does_not_reach_persistent_chat(self):
        chat=java("ChatStore")
        task=java("TaskStore")
        accessibility=java("WolfAccessibilityService")
        self.assertIn("SensitiveText.forStorage(", chat)
        self.assertIn("SensitiveText.containsCredential(", task)
        self.assertIn("hasSensitiveField(root)", accessibility)
        self.assertIn("AssistivePolicy.isSensitiveField(", accessibility)
        self.assertIn("return !queue.isEmpty();", accessibility)

    def test_dynamic_local_fallback_is_never_paid_or_persistent(self):
        resource=java("LocalResourcePolicy")
        monitor=java("LocalResourceGuard")
        thinker=java("OfflineThinker")
        embedded=java("EmbeddedAi")
        home=java("HomeActivity")
        self.assertIn("ActivityManager.MemoryInfo", monitor)
        self.assertIn("getCurrentThermalStatus()", monitor)
        self.assertIn("BatteryManager.EXTRA_LEVEL", monitor)
        self.assertIn("resources.steps(", thinker)
        self.assertIn("resources.tokens(", thinker)
        self.assertIn("resources.useImported(imported.length())", embedded)
        self.assertIn("EmbeddedAi.unloadWhenIdle()", home)
        self.assertIn("onTrimMemory(int level)", home)
        self.assertNotIn("HfBrainClient", resource)
        self.assertNotIn("HfBrainClient", monitor)
        self.assertNotIn("HfBrainClient", thinker)
        self.assertNotIn("CostGuard.setFreeOnly", resource)

    def test_oauth_refresh_rotates_once_and_doesnt_delete_on_expiry(self):
        oauth=java("MetricoolOAuth")
        self.assertIn("public synchronized String accessToken()", oauth)
        self.assertIn("rotated=response.optString", oauth)
        self.assertIn("if(rotated.isEmpty())rotated=existingRefresh", oauth)
        expired=oauth.split('if (refresh.isEmpty()) {', 1)[1].split('}', 1)[0]
        self.assertNotIn("disconnect();", expired)
        self.assertIn("AndroidKeyStore", oauth)
        self.assertNotIn("putString(\"access_token\"", oauth)

    def test_unknown_dispatch_status_never_becomes_retriable_draft(self):
        task=java("TaskStore")
        ui=java("MainActivity")
        self.assertIn("boolean reserveDispatch(long id, String uid)", task)
        self.assertIn("void unknownDispatch(long id, String uid)", task)
        self.assertIn("status='draft'", task)
        self.assertIn("status='reserved'", task)
        self.assertIn("tasks.reserveDispatch(task.id,task.uid)", ui)
        self.assertIn("tasks.unknownDispatch(task.id,task.uid)", ui)
        self.assertIn("Исход отправки неизвестен", ui)
        self.assertIn("dispatchTask(item)", ui)

    def test_versions_and_ci_have_actual_contract_and_unit_coverage(self):
        gradle=(ROOT / "android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.3.0'", gradle)
        self.assertIn("versionCode 29", gradle)
        workflow=(ROOT / ".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("WolfPrivacyResourceTest.java", workflow)
        self.assertIn("unittest discover -s tests -p 'test_wolf*.py'", workflow)
        self.assertIn("tests/**", workflow)


if __name__=="__main__":
    unittest.main()
