"""WOLF 15: real source safety contracts. No model downloads or payments."""
from pathlib import Path
import unittest

ROOT = Path("android/app/src/main/java/ai/wolf/autopilot")


def java(name):
    return (ROOT/(name+".java")).read_text("utf-8")


class FreeFirstContracts(unittest.TestCase):
    def test_default_zero_cost_enforced_before_billing(self):
        guard=java("CostGuard")
        client=java("HfBrainClient")
        home=java("HomeActivity")
        settings=java("CloudBrainSettingsActivity")
        self.assertIn('.getBoolean(KEY,true)',guard)
        self.assertIn('CostGuard.requireExternalInferenceEnabled(app);',client)
        self.assertLess(
            client.index('CostGuard.requireExternalInferenceEnabled(app);'),
            client.index('reserve(app.getApplicationContext());'))
        self.assertIn('setFreeOnly(this,true)',home)
        self.assertIn('setFreeOnly(this,false)',settings)
        self.assertIn('setTitle("Разрешить платные облачные API?")',settings)
        self.assertIn('CostGuard.freeOnly(this)',home)
        self.assertNotIn('HfBrainClient.ask(',java("OfflineThinker"))

    def test_download_manager_wifi_and_owner_confirmed_install(self):
        app=java("ModelCatalogActivity")
        vault=java("ModelVault")
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn('ModelCatalogActivity',manifest)
        self.assertIn('DownloadManager.Request.NETWORK_WIFI',app)
        self.assertIn('request.setAllowedOverMetered(false)',app)
        self.assertIn('request.setAllowedOverRoaming(false)',app)
        self.assertIn('request.setDestinationInExternalFilesDir(',app)
        self.assertIn('DownloadManager.STATUS_SUCCESSFUL',app)
        self.assertIn('new AlertDialog.Builder(this)',app)
        self.assertIn('ModelVault.installVerifiedDownload(',app)
        self.assertIn('entry.sha256',vault)
        self.assertIn('MessageDigest.getInstance("SHA-256")',vault)
        self.assertIn('if(!persisted)',vault)
        self.assertIn('backup.renameTo(active)',vault)
        self.assertIn('source.getCanonicalPath().equals(allowed.getCanonicalPath())',vault)
        self.assertIn('partial.delete();',vault)
        self.assertNotIn('HfBrainClient',app)

    def test_real_three_pass_inference_with_local_sources_only(self):
        agent=java("OfflineThinker")
        router=java("WolfCommandRouter")
        home=java("HomeActivity")
        self.assertIn('DEEP_LOCAL',router)
        self.assertIn('OfflineThinker.run(',home)
        self.assertIn('DocumentAssistant.ask(',agent)
        self.assertEqual(agent.count('EmbeddedAi.ask('),3)
        self.assertIn('Самопроверка (не независимая экспертиза)',agent)
        self.assertIn('callback.stage("1/"+stages',agent)
        self.assertIn('callback.stage("3/"+stages',agent)
        self.assertNotIn('WebSearchService',agent)
        self.assertNotIn('HttpsURLConnection',agent)
        self.assertNotIn('GithubOperations',agent)
        self.assertNotIn('HfBrainClient',agent)

    def test_models_not_mandatory_and_app_version(self):
        model=java("EmbeddedAi")
        self.assertIn('qwen2.5-coder-0.5b-instruct-q4_0.gguf',model)
        self.assertIn('.contains("qwen3")',model)
        self.assertIn('ModelVault.active(app)',model)
        text=Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.0.0'",text)
        self.assertIn('versionCode 26',text)
        workflow=Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn('WolfFreeFirstTest.java',workflow)


if __name__=="__main__":
    unittest.main()
