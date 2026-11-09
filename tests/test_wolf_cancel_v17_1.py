"""17.1 regression: never lock Send during model loading; native cancellation
must be asynchronous and results from stale jobs cannot poison the chat."""
import unittest
from pathlib import Path

ROOT = Path("android/app/src/main/java/ai/wolf/autopilot")
NATIVE = Path("android/app/src/main/cpp/wolf_native.cpp")


def java(name):
    return (ROOT / (name + ".java")).read_text("utf-8")


class HungChatRegression(unittest.TestCase):
    def test_send_never_disabled_and_stop_is_visible(self):
        home = java("HomeActivity")
        self.assertIn("send.setEnabled(true);", home)
        self.assertNotIn("send.setEnabled(!value)", home)
        self.assertIn('stopAnswer = button("⏹ Остановить ответ', home)
        self.assertIn('stopAnswer.setOnClickListener(v -> cancelBackground(', home)
        self.assertIn('stopAnswer.setVisibility(value ? View.VISIBLE : View.GONE)', home)
        self.assertIn('if (busy) cancelBackground("Получен новый вопрос.', home)
        self.assertNotIn("if (busy) return;\n        String command = input", home)

    def test_timeout_rotates_worker_and_ignores_stale_callbacks(self):
        home = java("HomeActivity")
        self.assertIn("RESPONSE_TIMEOUT_MS = 120_000L", home)
        self.assertIn("activeRequestSerial++", home)
        self.assertIn("Future<?> oldJob = activeJob;", home)
        self.assertIn("oldJob.cancel(true)", home)
        self.assertIn("worker.shutdownNow();", home)
        self.assertIn("worker = Executors.newSingleThreadExecutor();", home)
        self.assertIn("if (request != activeRequestSerial", home)
        self.assertIn("ui.postDelayed(activeTimeout, RESPONSE_TIMEOUT_MS)", home)
        self.assertIn("clearTimeout();", home)
        self.assertIn("stopAnswer.setVisibility", home)
        self.assertIn("Действие", home)

    def test_java_cancel_epoch_before_and_after_native_load(self):
        source = java("EmbeddedAi")
        self.assertIn("private static final AtomicLong CANCEL_EPOCH", source)
        self.assertIn("public static void cancelCurrent()", source)
        self.assertIn("CANCEL_EPOCH.incrementAndGet();", source)
        self.assertIn("nativeCancel();", source)
        self.assertIn("nativeResetCancel();", source)
        self.assertIn("long epoch=CANCEL_EPOCH.get();", source)
        self.assertIn("throwIfCancelled(epoch);\n        check(context);", source)
        self.assertIn("throwIfCancelled(epoch);\n            check(context);", source)
        self.assertIn("nativeResetCancel();\n            throwIfCancelled(epoch);", source)
        self.assertIn("File fallback=model(app);", source)
        self.assertIn("nativeUnload();\n                loadedPath = null;", source)

    def test_native_stop_does_not_wait_for_generation_mutex(self):
        source = NATIVE.read_text("utf-8")
        self.assertIn("#include <atomic>", source)
        self.assertIn("std::atomic<bool> cancel_requested", source)
        self.assertIn('EmbeddedAi_nativeCancel(JNIEnv *, jclass)', source)
        self.assertIn('EmbeddedAi_nativeResetCancel(JNIEnv *, jclass)', source)
        section = source.split("EmbeddedAi_nativeCancel(JNIEnv *, jclass)", 1)[1]
        section = section.split("EmbeddedAi_nativeResetCancel", 1)[0]
        self.assertNotIn("std::lock_guard", section)
        self.assertIn("cancel_requested.store(true", section)
        self.assertIn("cancel_requested.load(std::memory_order_acquire)", source)
        self.assertIn("std::chrono::seconds(100)", source)
        self.assertIn("if (timed_out)", source)
        self.assertIn("Генерация остановлена", source)

    def test_fast_fallback_is_user_confirmed_and_no_paid_api(self):
        home=java("HomeActivity")
        route=java("WolfCommandRouter")
        model=java("ModelVault")
        self.assertIn("case FAST_MODEL:", home)
        self.assertIn("private void confirmFastModel()", home)
        self.assertIn(".setPositiveButton(\"Переключить\"", home)
        self.assertIn("ModelVault.suspendUnsupported(this,", home)
        self.assertIn("Kind.FAST_MODEL", route)
        self.assertIn("putBoolean(\"enabled\",false)", model)
        self.assertNotIn("HfBrainClient", home.split("private void confirmFastModel()", 1)[1].split(
            "private void openPhoneAiProfile()",1)[0])
        gradle=Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.1.0'", gradle)
        self.assertIn("versionCode 27", gradle)


if __name__ == "__main__":
    unittest.main()
