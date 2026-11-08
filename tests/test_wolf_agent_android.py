"""WOLF AI 6 Android/CI agent integration safety contracts.

These are structural tests; actual Android runtime and GitHub permission
behavior must still be verified on the owner's device/account.
"""
import unittest
from pathlib import Path

JAVA = Path("android/app/src/main/java/ai/wolf/autopilot")
WORKFLOW = Path(".github/workflows/wolf-ai-agent.yml")


class AgentAndroidContracts(unittest.TestCase):
    def test_workflow_can_only_run_fixed_readonly_repositories(self):
        body = WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("permissions:\n  contents: read", body)
        self.assertIn("safe_fix:", body)
        self.assertIn("wolf_ai.agent_engine", body)
        self.assertIn('if [[ "$SAFE_FIX" == "true"', body)
        self.assertIn('TARGET_REPO=19wolfalone92/WOLF-AI', body)
        self.assertIn("if-no-files-found: warn", body)
        self.assertNotIn("git push ", body)
        self.assertNotIn("create-pull-request@", body)
        self.assertNotIn("video.publish", body)

    def test_android_writes_only_after_confirmation(self):
        ui = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        api = (JAVA / "GithubApi.java").read_text(encoding="utf-8")
        store = (JAVA / "TaskStore.java").read_text(encoding="utf-8")
        self.assertIn('setPositiveButton("Разрешить одну миссию"', ui)
        self.assertIn("GithubApi.dispatchAgent", ui)
        self.assertIn("tasks.reserveAgent", ui)
        self.assertIn("tasks.unknownAgent", ui)
        self.assertIn("reconcileAgent(list)", ui)
        self.assertIn('AGENT_WORKFLOW = "/actions/workflows/wolf-ai-agent.yml"', api)
        self.assertIn("GithubBacklog.suspectedSecret(goal)", api)
        self.assertIn('safeFix && !("wolf".equals(project)', api)
        self.assertIn("agent_status IN ('none','success','failure')", store)
        self.assertIn("agent_submitted_at", store)
        self.assertIn("oldVersion < 4", store)

    def test_local_agent_never_runs_generated_commands(self):
        local = (JAVA / "LocalToolAgent.java").read_text(encoding="utf-8")
        ui = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("WikipediaResearch.search", local)
        self.assertIn("GithubApi.runs", local)
        self.assertIn("memory.related(question, 3)", local)
        self.assertIn("EmbeddedAi.ask", local)
        self.assertNotIn("Runtime.getRuntime().exec", local)
        self.assertNotIn("ProcessBuilder(", local)
        self.assertIn('setPositiveButton("Запустить агента"', ui)
        self.assertIn("ЖУРНАЛ ИНСТРУМЕНТОВ", ui)

    def test_mission_workflow_migrates_without_losing_old_runs(self):
        missions = Path("wolf_ai/missions.py").read_text(encoding="utf-8")
        self.assertIn('WORKFLOW = "wolf-ai-agent.yml"', missions)
        self.assertIn('LEGACY_AUDIT_WORKFLOW = "wolf-ai-audit.yml"', missions)
        self.assertIn('"safe_fix": "false"', missions)
        self.assertIn("mission.goal", missions)
        self.assertIn("FORBIDDEN.search(goal)", missions)


if __name__ == "__main__":
    unittest.main()
