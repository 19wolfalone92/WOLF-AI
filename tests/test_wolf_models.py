"""Offline model-slot contracts (structural; Android runtime requires a device)."""
from pathlib import Path
import re
import unittest

JAVA = Path("android/app/src/main/java/ai/wolf/autopilot")
NATIVE = Path("android/app/src/main/cpp/wolf_native.cpp")


class LocalModelContracts(unittest.TestCase):
    def test_model_vault_uses_only_system_selected_files(self):
        src = (JAVA / "ModelVault.java").read_text(encoding="utf-8")
        self.assertIn('!"content".equals(uri.getScheme())', src)
        self.assertIn("getContentResolver().openInputStream(uri)", src)
        self.assertIn('new File(c.getFilesDir(), "models")', src)
        self.assertIn("MessageDigest.getInstance(\"SHA-256\")", src)
        self.assertIn("partial.renameTo(existing)", src)
        self.assertIn("oldBackup.renameTo(existing)", src)
        self.assertIn("MAX_GGUF_BYTES = 3_600_000_000L", src)
        self.assertIn("GGUF", src)
        self.assertNotIn("HttpURLConnection", src)
        self.assertNotIn("Authorization", src)

    def test_model_switch_updates_native_model_path(self):
        src = (JAVA / "EmbeddedAi.java").read_text(encoding="utf-8")
        self.assertIn("ModelVault.active(app)", src)
        self.assertIn("loadedPath = path", src)
        self.assertIn('if (!path.equals(loadedPath))', src)
        self.assertIn("synchronized (LOCK)", src)
        self.assertIn("nativeUnload()", src)
        self.assertIn("Не выдумывай факты", src)

    def test_native_has_larger_but_bounded_context_and_sampling(self):
        src = NATIVE.read_text(encoding="utf-8")
        self.assertIn("cparams.n_ctx = 4096;", src)
        self.assertIn("min((int) max_out, 768)", src)
        self.assertIn("llama_sampler_init_top_p(0.90f, 1)", src)
        self.assertIn("llama_sampler_init_temp(0.65f)", src)
        self.assertIn("needed > 2500", src)
        self.assertNotIn("llama_sampler_init_greedy()", src)

    def test_voice_explicit_opt_in(self):
        src = (JAVA / "SpeechCenter.java").read_text(encoding="utf-8")
        screen = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("new TextToSpeech(context", src)
        self.assertIn("🔊 Озвучить последний ответ", screen)
        self.assertIn("speaker.speak(lastAiAnswer)", screen)
        self.assertIn("speaker.close()", screen)
        self.assertIn("IMPORT_GGUF", screen)
        self.assertIn("ACTION_OPEN_DOCUMENT", screen)
        self.assertIn("импортировать более мощную gguf-модель", screen.lower())


if __name__ == "__main__":
    unittest.main()
