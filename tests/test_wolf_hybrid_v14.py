"""WOLF AI 14 hybrid inference and authenticated browser regression contracts."""
from pathlib import Path
import unittest

ROOT=Path("android/app/src/main/java/ai/wolf/autopilot")


def src(name):
    return (ROOT/f"{name}.java").read_text("utf-8")


class HybridReleaseContracts(unittest.TestCase):
    def test_real_opt_in_provider_and_non_reusable_credentials(self):
        vault=src("CloudBrainStore")
        api=src("HfBrainClient")
        self.assertIn('AndroidKeyStore',vault)
        self.assertIn('AES/GCM/NoPadding',vault)
        self.assertIn('WOLF_CLOUD_HF_AES_V1',vault)
        self.assertIn('WOLF_GITHUB_PAT_AES_V1',src("SecureStore"))
        self.assertNotIn('WOLF_GITHUB_PAT_AES_V1',vault)
        self.assertIn('router.huggingface.co/v1/chat/completions',api)
        self.assertIn('DAILY_REQUEST_CAP = 12',api)
        self.assertIn('reserve(app.getApplicationContext())',api)
        self.assertIn('setInstanceFollowRedirects(false)',api)
        self.assertIn('code==402||code==429',api)
        self.assertIn('new JSONObject()',api)
        self.assertNotIn('chat.recent(',api)
        self.assertNotIn('KnowledgeBase',api)
        self.assertNotIn('GitHub PAT',api)
        self.assertNotIn('DocumentAssistant.ask',api)
        self.assertNotIn('Runtime.getRuntime().exec',api)

    def test_cloud_call_only_after_user_approves_exact_question(self):
        home=src("HomeActivity")
        router=src("WolfCommandRouter")
        self.assertIn('CLOUD_AI',router)
        self.assertIn('case CLOUD_AI:',home)
        self.assertIn('CloudBrainPolicy.question(question)',home)
        self.assertIn('HfBrainClient.ask(getApplicationContext()',home)
        self.assertIn('setTitle("Отправить один вопрос в Hugging Face?")',home)
        self.assertIn('setNegativeButton("Отмена"',home)
        self.assertIn('setPositiveButton("Разрешаю один запрос"',home)
        self.assertIn('CloudBrainSettingsActivity.class',home)
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn('CloudBrainSettingsActivity',manifest)

    def test_logged_in_browser_form_draft_is_constrained(self):
        browser=src("BrowserActivity")
        helper=src("AuthenticatedFormPolicy")
        self.assertIn('showFormDraft()',browser)
        self.assertIn('showLocalPageAnalysis()',browser)
        self.assertIn('AuthenticatedFormPolicy.script(',browser)
        self.assertIn('EmbeddedAi.ask(getApplicationContext()',browser)
        self.assertIn('page.evaluateJavascript(',browser)
        self.assertIn('setTitle("Подтверди ввод без отправки")',browser)
        self.assertIn('matches.length!==1',helper)
        self.assertIn("input[type=text]",helper)
        self.assertIn("one-time-code",helper)
        self.assertNotIn('document.cookie',helper)
        self.assertNotIn('.submit(',helper)
        self.assertNotIn('HfBrainClient',browser)
        self.assertNotIn('GithubCloudBrowser',browser)

    def test_user_owned_playwright_private_companion_keeps_auth_off_ci(self):
        script=Path("wolf_ai/private_browser.py").read_text("utf-8")
        self.assertIn('prohibit_ci()',script)
        self.assertIn('launch_persistent_context',script)
        self.assertIn('headless=False',script)
        self.assertIn('profile_directory',script)
        self.assertIn('accept_downloads=False',script)
        self.assertNotIn('storage_state(',script)
        self.assertNotIn('upload-artifact',script)
        self.assertNotIn('requests.post(',script)

    def test_real_read_only_agent_and_accessibility_preview(self):
        agent=src("PublicResearchAgent")
        home=src("HomeActivity")
        accessibility=src("WolfAccessibilityService")
        self.assertIn("WebSearchService.search(queries[i])",agent)
        self.assertIn("WebPageReader.read(result.url)",agent)
        self.assertIn("EmbeddedAi.ask(context,prompt",agent)
        self.assertIn("if(attempted>=4)break",agent)
        self.assertIn("seen.add(result.url)",agent)
        self.assertIn("append(verified)",agent)
        self.assertIn("case AUTONOMOUS_RESEARCH:",home)
        self.assertIn("confirmAutonomousResearch(command)",home)
        self.assertIn("PublicResearchAgent.run(",home)
        self.assertIn('setTitle("🤖 Запустить публичного исследовательского агента?")',home)
        self.assertIn('listNavigationLabels(root)',accessibility)
        self.assertIn('if ("inspect".equals(request.type))',accessibility)
        self.assertIn('!node.isPassword()',accessibility)
        self.assertIn('WolfAccessibilityService.status()',home)

    def test_android_compilation_gates_updated(self):
        version=Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.2.0'",version)
        workflow=Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("WolfHybridBrainTest.java",workflow)
        self.assertIn("test_wolf_private_browser.py",workflow)


if __name__=="__main__":
    unittest.main()
