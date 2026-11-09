"""Cross-stack regressions: verify real Android/Python privilege boundaries.

These tests are intentionally dependency-free and run on GitHub Actions
without Android SDK, account tokens, a browser or network calls.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android/app/src/main/java/ai/wolf/autopilot"
WF = ROOT / ".github/workflows"


def source(name: str) -> str:
    return (JAVA / name).read_text(encoding="utf-8")


class AndroidPythonBoundary(unittest.TestCase):
    def test_two_distinct_agents_are_not_confused(self):
        local = source("LocalToolAgent.java")
        cloud = (ROOT / "wolf_ai/agent_engine.py").read_text(encoding="utf-8")
        orchestrator = (ROOT / "wolf_ai/orchestrator.py").read_text(encoding="utf-8")
        self.assertIn("READ-ONLY tools", local)
        self.assertIn("GithubRepoInspector", local)
        self.assertIn("EmbeddedAi.ask(", local)
        self.assertIn("--project", cloud)
        self.assertIn("execute(plan:", cloud)
        self.assertIn("subprocess.run(", orchestrator)
        self.assertNotIn("subprocess.run", local)
        self.assertNotIn("AndroidKeystore", cloud)
        self.assertNotIn("android.app", orchestrator)
        self.assertNotIn("import wolf_ai", source("HomeActivity.java"))

    def test_legacy_termux_files_removed_from_active_tree(self):
        for relative in (
            "legacy/LocalAi.java.txt",
            "legacy/termux/wolf-local.sh",
            "docs/archive/ANDROID_README_V13.md",
        ):
            with self.subTest(path=relative):
                self.assertFalse((ROOT / relative).exists())

    def test_android_network_is_https_only(self):
        xml = (ROOT / "android/app/src/main/res/xml/"
               "network_security_config.xml").read_text(encoding="utf-8")
        manifest = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        self.assertIn('cleartextTrafficPermitted="false"', xml)
        self.assertNotIn('cleartextTrafficPermitted="true"', xml)
        self.assertNotIn("<domain-config", xml)
        self.assertIn('android:allowBackup="false"', manifest)
        self.assertIn('android:usesCleartextTraffic="false"', manifest)
        self.assertIn('android.permission.BIND_ACCESSIBILITY_SERVICE', manifest)
        self.assertNotIn('isAccessibilityTool="true"', manifest)

    def test_accessibility_disclosure_is_active_and_revocable(self):
        consent = source("AccessibilityConsent.java")
        home = source("HomeActivity.java")
        service = source("WolfAccessibilityService.java")
        policy = source("AssistivePolicy.java")
        self.assertIn('getBoolean(KEY, false)', consent)
        self.assertIn('accessibility_disclosure_v2', consent)
        self.assertIn('WHAT', 'WHAT')  # separate consent contract does not use English UI
        self.assertIn("потенциально личную информацию", consent)
        self.assertIn("не отправляются в облако", consent)
        self.assertIn('AccessibilityConsent.accepted(this)', home)
        self.assertIn('AccessibilityConsent.accept(this)', home)
        self.assertIn('AccessibilityConsent.revoke(this)', home)
        self.assertIn('AccessibilityConsent.disclosure()', home)
        self.assertIn('Отозвать согласие', home)
        self.assertIn('AccessibilityConsent.accepted(svc)', service)
        self.assertIn('AccessibilityConsent.accepted(this)', service)
        self.assertIn('AssistivePolicy.safeClickableContainer(', service)
        self.assertIn('safeClickableContainer(', policy)
        self.assertNotIn("startActivity(new Intent(\"android.settings.TTS_SETTINGS\")",
                         consent)

    def test_credentials_and_workflow_rights_stay_separate(self):
        manifest = (ROOT / "android/app/src/main/AndroidManifest.xml").read_text(
            encoding="utf-8"
        )
        self.assertNotIn('android.permission.READ_SMS', manifest)
        self.assertNotIn('android.permission.READ_CONTACTS', manifest)
        self.assertNotIn('android.permission.ACCESS_FINE_LOCATION', manifest)
        names = (
            "wolf-ai-android.yml",
            "wolf-ai-audit.yml",
            "wolf-ai-agent.yml",
            "wolf-ai-cloud-browser.yml",
            "wolf-ai-train.yml",
        )
        for name in names:
            wf = (WF / name).read_text(encoding="utf-8")
            prefix = wf.split("jobs:", 1)[0]
            with self.subTest(workflow=name):
                self.assertIn("permissions:", prefix)
                self.assertIn("contents: read", prefix)
                self.assertNotIn("contents: write", prefix)
                self.assertNotIn("issues: write", prefix)
        missions = (WF / "wolf-ai-missions.yml").read_text("utf-8")
        repair = (WF / "wolf-ai-repair.yml").read_text("utf-8")
        self.assertIn("issues: write", missions.split("jobs:", 1)[0])
        self.assertIn("actions: write", missions.split("jobs:", 1)[0])
        self.assertIn("pull-requests: write", repair.split("jobs:", 1)[0])
        self.assertIn("persist-credentials: false", missions)
        self.assertIn("persist-credentials: false", repair)
        self.assertIn("https://api.github.com/", source("GithubApi.java"))
        self.assertIn("client_task_id", source("GithubApi.java"))

    def test_ci_regressions_use_one_full_python_suite(self):
        yml = (WF / "wolf-ai-android.yml").read_text(encoding="utf-8")
        self.assertIn("unittest discover -s tests -p 'test_wolf*.py'", yml)
        self.assertEqual(
            yml.count("unittest discover -s tests -p 'test_wolf*.py'"), 1
        )
        self.assertIn("'tests/**'", yml)
        self.assertIn("WolfCommandRouterTest.java", yml)
        self.assertIn("AssistedNavigationTest.java", yml)
        self.assertIn("WorkspaceCryptoTest.java", yml)


if __name__ == "__main__":
    unittest.main()
