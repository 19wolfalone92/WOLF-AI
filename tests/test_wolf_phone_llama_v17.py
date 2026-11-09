"""WOLF 17 portable AI video / genuine on-device Samsung CPU-RAM adapt tests."""
import unittest
from pathlib import Path

ROOT=Path("android/app/src/main/java/ai/wolf/autopilot")
def java(name):return (ROOT/(name+".java")).read_text("utf-8")

class SamsungMobileQwen35Contracts(unittest.TestCase):
    def test_model_sources_are_sha_pinned_and_local(self):
        catalog=java("OfflineModelCatalog")
        for item in (
            "QWEN35_08", "QWEN35_2", "QWEN35_4",
            "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf",
            "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223",
            "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4",
            "Qwen3.5-0.8B-Q4_0.gguf",
            "Qwen3.5-2B-Q4_K_M.gguf",
            "Qwen3.5-4B-Q4_K_M.gguf",
        ):
            self.assertIn(item,catalog)
        self.assertIn('entry.sha256.matches("[0-9a-f]{64}")',catalog)
        self.assertIn("https://huggingface.co/",catalog)
        self.assertNotIn(".llamafile",catalog)
        self.assertNotIn("Runtime.getRuntime().exec",catalog)

    def test_reads_actual_android_memory_not_just_model_name(self):
        screen=java("PhoneAiProfileActivity")
        profile=java("DeviceAiProfile")
        for marker in (
            "ActivityManager.MemoryInfo()", "info.totalMem", "info.availMem",
            "stat.getAvailableBytes()", "Build.SOC_MODEL",
            "Build.VERSION.RELEASE", "Build.SUPPORTED_64_BIT_ABIS",
            "getCurrentThermalStatus()", "Runtime.getRuntime().availableProcessors()",
        ):
            self.assertIn(marker,screen)
        self.assertIn("QWEN35_08",profile)
        self.assertIn("QWEN35_2",profile)
        self.assertIn("QWEN35_4",profile)
        self.assertIn("totalRam>=11*GB",profile)
        self.assertIn("availableRam>=3_800_000_000L",profile)
        self.assertIn("freeBytes>=6_300_000_000L",profile)
        for leak in ("IMEI","SERIAL","ANDROID_ID","TelephonyManager",
                     "HttpURLConnection","HfBrainClient","GithubOperations"):
            self.assertNotIn(leak,screen)
        self.assertNotIn("HttpURLConnection",profile)

    def test_context_ram_budget_and_history_pipeline(self):
        local=java("EmbeddedAi")
        native=Path("android/app/src/main/cpp/wolf_native.cpp").read_text("utf-8")
        vault=java("ModelVault")
        self.assertIn('PhoneAiProfileActivity.current(context).contextTokens',local)
        self.assertIn('nativeGenerate(body, budget, contextTokens)',local)
        self.assertIn('history.size() - historyLimit',local)
        self.assertIn('String output',local)
        self.assertIn('cparams.n_ctx = ctx_tokens;',native)
        self.assertIn('requested_ctx >= 4096',native)
        self.assertIn('requested_ctx >= 3072',native)
        self.assertIn('pos < ctx_tokens - 1',native)
        self.assertIn('suspendUnsupported(',vault)
        self.assertIn('ModelVault.suspendUnsupported(app, result)',local)
        self.assertIn('File fallback=model(app);',local)
        self.assertNotIn('Runtime.getRuntime().exec',local)

    def test_access_from_home_and_catalog(self):
        home=java("HomeActivity")
        router=java("WolfCommandRouter")
        catalog=java("ModelCatalogActivity")
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn('PhoneAiProfileActivity',manifest)
        self.assertIn('case PHONE_PROFILE:',home)
        self.assertIn('openPhoneAiProfile()',home)
        self.assertIn('Kind.PHONE_PROFILE',router)
        self.assertIn('PhoneAiProfileActivity.current(this)',catalog)
        self.assertIn('DeviceAiProfile.warning(entry,advice)',catalog)
        self.assertIn('QWEN35_2',catalog)
        self.assertIn('QWEN35_4',catalog)
        self.assertIn('QWEN35_08',catalog)
        self.assertIn('chat.recent(12)',home)
        self.assertIn('Разговаривай естественно',java('EmbeddedAi'))

    def test_truthful_docs_release(self):
        d=Path("docs/WOLF_17_PHONE_LLAMA_VIDEO_RU.md").read_text("utf-8")
        self.assertIn("Mozilla",d)
        self.assertIn("не ChatGPT",d)
        self.assertIn("Windows",d)
        self.assertIn("Android",d)
        self.assertIn("не замеряли",d.lower())
        gradle=Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.0.0'",gradle)
        self.assertIn("versionCode 26",gradle)
        workflow=Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("WolfPhoneProfileTest.java",workflow)

if __name__=="__main__":
    unittest.main()
