"""WOLF 10 chat-first release contracts and Android safety boundaries."""
from pathlib import Path
import unittest

ROOT = Path("android/app/src/main/java/ai/wolf/autopilot")


def java(name):
    return (ROOT / (name + ".java")).read_text(encoding="utf-8")


class WOLFChatFirstContracts(unittest.TestCase):
    def test_launcher_is_new_home_without_breaking_existing_oauth(self):
        manifest = Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        home = manifest.index('android:name=".HomeActivity"')
        launcher = manifest.index("android.intent.category.LAUNCHER")
        legacy = manifest.index('android:name=".MainActivity"')
        self.assertLess(home, launcher)
        self.assertLess(launcher, legacy)
        self.assertIn('android:host="oauth"', manifest)
        self.assertIn('android:name=".BrowserActivity" android:exported="false"', manifest)
        self.assertIn('android:name=".GuideActivity" android:exported="false"', manifest)
        self.assertIn('android.webkit.WebView.EnableSafeBrowsing', manifest)
        self.assertIn('android:usesCleartextTraffic="false"', manifest)

    def test_primary_ui_is_chat_not_a_control_dashboard(self):
        home = java("HomeActivity")
        self.assertIn("WOLF AI", home)
        self.assertIn("Напиши WOLF любую команду", home)
        self.assertIn("renderHistory()", home)
        self.assertIn("chat.recent(20)", home)
        self.assertIn("chat.add(fromUser ? ", home)
        self.assertIn("WolfCommandRouter.route(command)", home)
        self.assertIn("case PROJECT_TASK:", home)
        self.assertIn("case READ_URL:", home)
        self.assertIn("case WEB_SEARCH:", home)
        self.assertIn("case DEVICE:", home)
        self.assertIn("case TOOLS:", home)
        self.assertIn("DocumentAssistant.ask", home)
        self.assertIn("openGuide()", home)
        self.assertIn("openTools()", home)
        self.assertIn("speaker.speak(text)", home)
        self.assertIn("R.drawable.wolf_logo", home)
        self.assertIn('setTitle("Разрешить действие на телефоне?")', home)
        self.assertIn('setTitle("Отправить задачу агенту GitHub?")', home)

    def test_browser_blocks_dangerous_webview_privileges(self):
        browser = java("BrowserActivity")
        for anchor in (
            "new WebView(this)",
            "settings.setJavaScriptEnabled(false)",
            "settings.setAllowFileAccess(false)",
            "settings.setAllowContentAccess(false)",
            "settings.setAllowFileAccessFromFileURLs(false)",
            "settings.setAllowUniversalAccessFromFileURLs(false)",
            "settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)",
            "settings.setSafeBrowsingEnabled(true)",
            "setAcceptThirdPartyCookies(page, false)",
            "handler.cancel()",
            "callback.backToSafety(true)",
            "html.duckduckgo.com/html/?q=",
            "setPositiveButton(\"Включить для вкладки\"",
        ):
            self.assertIn(anchor, browser, anchor)
        self.assertNotIn("addJavascriptInterface", browser)
        self.assertNotIn("setJavaScriptEnabled(true);", browser.split(
            "scripts.setOnClickListener", 1)[0])

    def test_phone_only_uses_system_intents_without_privileged_permissions(self):
        phone = java("DeviceActions")
        manifest = Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        for anchor in (
            "Settings.ACTION_SETTINGS",
            "Settings.ACTION_WIFI_SETTINGS",
            "Settings.ACTION_BLUETOOTH_SETTINGS",
            "AlarmClock.ACTION_SHOW_ALARMS",
            "Intent.ACTION_DIAL",
            "Intent.ACTION_SENDTO",
            "Intent.ACTION_OPEN_DOCUMENT",
            "MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA",
            "Intent.CATEGORY_APP_CALENDAR",
            "Intent.ACTION_BATTERY_CHANGED",
        ):
            self.assertIn(anchor, phone)
        for banned in (
            "QUERY_ALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE",
            "READ_SMS", "READ_CONTACTS", "CALL_PHONE",
            "BIND_ACCESSIBILITY_SERVICE", "SYSTEM_ALERT_WINDOW",
            "REQUEST_INSTALL_PACKAGES", "WRITE_SECURE_SETTINGS",
        ):
            self.assertNotIn(banned, manifest)
        self.assertNotIn("ACTION_CALL", phone)
        self.assertNotIn("Runtime.getRuntime().exec", phone)

    def test_public_webpage_reader_is_bounded_and_user_confirmed(self):
        source = java("WebPageReader")
        home = java("HomeActivity")
        self.assertIn('!"https".equalsIgnoreCase(uri.getScheme())', source)
        self.assertIn("InetAddress.getAllByName(host)", source)
        self.assertIn("con.setInstanceFollowRedirects(false)", source)
        self.assertIn('con.setRequestMethod("GET")', source)
        self.assertIn("buf.size() + n > 140000", source)
        self.assertIn("!type.toLowerCase(Locale.ROOT).contains", source)
        self.assertIn("getUserInfo() != null", source)
        self.assertNotIn('con.setRequestMethod("POST")', source)
        self.assertNotIn("setRequestProperty(\"Authorization\"", source)
        self.assertIn('setTitle("Прочитать публичный сайт?")', home)
        self.assertIn("WebPageReader.read(url)", home)

    def test_voice_needs_user_trigger_and_queues_first_answer(self):
        home = java("HomeActivity")
        tts = java("SpeechCenter")
        self.assertIn("RecognizerIntent.ACTION_RECOGNIZE_SPEECH", home)
        self.assertIn("startActivityForResult(intent, VOICE)", home)
        self.assertIn("aloud = !aloud", home)
        self.assertIn("queuedText = text", tts)
        self.assertIn("new Handler(Looper.getMainLooper())", tts)
        self.assertIn("queuedText = null", tts)

    def test_user_guide_has_practical_russian_explanations(self):
        guide = java("GuideActivity")
        for anchor in ("Как работает", "Интернет и браузер", "Разговор голосом",
                       "Управление телефоном", "Документы", "GitHub",
                       "Видеостудия", "Приватность"):
            self.assertIn(anchor, guide)
        gradle = Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '11.1.0'", gradle)


if __name__ == "__main__":
    unittest.main()
