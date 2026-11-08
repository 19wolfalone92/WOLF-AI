"""WOLF AI 11 realistic supervised autonomy release contracts.

These are structural checks. GitHub Actions also executes real Java routing and
batch-planner tests and compiles an installable Android APK.
"""
from pathlib import Path
import unittest

ROOT = Path("android/app/src/main/java/ai/wolf/autopilot")


def source(name):
    return (ROOT / f"{name}.java").read_text(encoding="utf-8")


class SupervisedAutonomyV11(unittest.TestCase):
    def test_android_launches_consent_first_chat_and_batch_publisher(self):
        manifest = Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn('android:name=".HomeActivity"', manifest)
        self.assertIn('android:name=".MetricoolBatchActivity" android:exported="false"', manifest)
        self.assertNotIn("BIND_ACCESSIBILITY_SERVICE", manifest)
        self.assertNotIn("QUERY_ALL_PACKAGES", manifest)
        self.assertIn("android.permission.RECORD_AUDIO", manifest)
        gradle = Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '11.1.0'", gradle)

    def test_research_is_multi_step_and_source_grounded(self):
        home = source("HomeActivity")
        report = source("ResearchReport")
        self.assertIn("case RESEARCH:", home)
        self.assertIn("confirmResearch(command)", home)
        self.assertIn('setTitle("Начать исследование?")', home)
        self.assertIn("ResearchReport.run(getApplicationContext(), subject)", home)
        self.assertIn("WebSearchService.search(topic.trim())", report)
        self.assertIn("WebPageReader.read(found.url)", report)
        self.assertIn("attempted < 3", report)
        self.assertIn("search.links()", report)
        self.assertNotIn("Runtime.getRuntime().exec", report)
        self.assertNotIn("HttpsURLConnection", report)

    def test_voice_conversation_is_explicit_and_activity_scoped(self):
        home = source("HomeActivity")
        speech = source("SpeechCenter")
        self.assertIn("voiceSession = !voiceSession", home)
        self.assertIn("speaker.setOnUtteranceDone(", home)
        self.assertIn("!speechListening", home)
        self.assertIn("startSpeechRecognition()", home)
        self.assertIn("else stopVoiceSession()", home)
        self.assertIn("UtteranceProgressListener", speech)
        self.assertIn("onDone(String utteranceId)", speech)
        self.assertIn("voiceSession = false;", home)
        self.assertNotIn("startForegroundService", home)
        self.assertNotIn("MediaRecorder", home)

    def test_batch_publisher_is_opt_in_and_fail_closed(self):
        ui = source("MetricoolBatchActivity")
        client = source("MetricoolMcp")
        parser = source("MetricoolBatchParser")
        self.assertIn("MetricoolBatchParser.parse", ui)
        self.assertIn("MetricoolBatchParser.preview", ui)
        self.assertIn("new AlertDialog.Builder(this)", ui)
        self.assertIn("publisher.schedule(post)", ui)
        self.assertIn("Пакет остановлен", ui)
        self.assertIn("if (items.size() >= 12)", parser)
        self.assertIn("ZoneId.of(timezone)", parser)
        self.assertIn("https", parser)
        self.assertIn('putBoolean("attempt_" + key, true).commit()', client)
        self.assertIn("findTool(", client)
        self.assertIn("Metricool MCP", ui)
        self.assertNotIn("setInterval", ui)
        self.assertNotIn("WebView", ui)
        workflow = Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("MetricoolBatchParserTest.java", workflow)
        self.assertIn("ai.wolf.autopilot.MetricoolBatchParserTest", workflow)

    def test_saf_export_has_explicit_user_file_selection(self):
        home = source("HomeActivity")
        self.assertIn("Intent.ACTION_CREATE_DOCUMENT", home)
        self.assertIn("startActivityForResult(save, EXPORT_REPORT)", home)
        self.assertIn('openOutputStream(output, "w")', home)
        self.assertIn("pendingExport", home)
        self.assertNotIn("WRITE_EXTERNAL_STORAGE", home)

    def test_added_phone_actions_use_only_native_confirmed_ui(self):
        route = source("WolfCommandRouter")
        phone = source("DeviceActions")
        home = source("HomeActivity")
        for phrase in ("поставь таймер", "поставь будильник", "настройки экрана"):
            self.assertIn(phrase, route)
        self.assertIn("AlarmClock.ACTION_SET_TIMER", phone)
        self.assertIn("AlarmClock.ACTION_SET_ALARM", phone)
        self.assertIn("AlarmClock.EXTRA_SKIP_UI, false", phone)
        self.assertIn("Settings.ACTION_DISPLAY_SETTINGS", phone)
        self.assertIn("Settings.ACTION_APPLICATION_DETAILS_SETTINGS", phone)
        self.assertIn('setTitle("Разрешить действие на телефоне?")', home)
        self.assertNotIn("ACTION_CALL", phone)


if __name__ == "__main__":
    unittest.main()
