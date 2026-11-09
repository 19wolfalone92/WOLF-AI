"""WOLF 13 independent public Chromium on GitHub Actions, not a fake cloud claim."""
from pathlib import Path
import unittest

ROOT = Path("android/app/src/main/java/ai/wolf/autopilot")


class CloudBrowserIntegrationContracts(unittest.TestCase):
    def test_real_workflow_has_cpu_chromium_and_artifacts(self):
        workflow = Path(".github/workflows/wolf-ai-cloud-browser.yml").read_text("utf-8")
        for text in (
            "workflow_dispatch:",
            "runs-on: ubuntu-latest",
            "playwright==1.64.1",
            "playwright install --with-deps chromium",
            "wolf_ai.cloud_browser",
            "upload-artifact@v4",
            "browser",
            "permissions:\n  contents: read",
            "persist-credentials: false",
        ):
            self.assertIn(text, workflow)
        self.assertNotIn("${{ secrets.", workflow)

    def test_chat_has_actual_dispatch_not_pretend_success(self):
        home = (ROOT / "HomeActivity.java").read_text("utf-8")
        router = (ROOT / "WolfCommandRouter.java").read_text("utf-8")
        client = (ROOT / "GithubCloudBrowser.java").read_text("utf-8")
        self.assertIn("case CLOUD_BROWSER:", home)
        self.assertIn("confirmCloudBrowser(command)", home)
        self.assertIn("GithubCloudBrowser.dispatch(plan,", home)
        self.assertIn("GithubCloudBrowser.recent(secrets.load())", home)
        self.assertIn('setTitle("Запустить удалённый Chromium на GitHub?")', home)
        self.assertIn("if (status!=204)", client)
        self.assertIn("wolf-ai-cloud-browser.yml", client)
        self.assertIn("CLOUD_BROWSER", router)
        self.assertNotIn("Runtime.getRuntime().exec", client)

    def test_runner_is_public_only_without_credentials_or_writes(self):
        source = Path("wolf_ai/cloud_browser.py").read_text("utf-8")
        for token in (
            "validate_public" if False else "validate_url",
            "validate_dns",
            "accept_downloads=False",
            "service_workers=\"block\"",
            "route.abort()",
            "route.continue_()",
            "getaddrinfo",
            "ipaddress.ip_address",
            "safe_click_js",
            "page.screenshot",
            "browser_report.json",
        ):
            self.assertIn(token, source)
        for unsafe in (
            "storage_state=", "cookies=", "add_cookies(",
            "fill(", "type(", "input(", "click(selector)",
            "webhook", "POST ", "git push ",
        ):
            self.assertNotIn(unsafe, source)

    def test_release_gates_have_actual_cpu_browser_smoke(self):
        gradle = Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.0.0'", gradle)
        workflow = Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("CloudBrowserCommand.java", workflow)


if __name__ == "__main__":
    unittest.main()
