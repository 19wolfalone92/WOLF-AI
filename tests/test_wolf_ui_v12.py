"""WOLF 12: explicit foreground-only assisted UI/browser automation.

CI source contracts augment executable JDK policy tests and Android assembly.
They do not claim universal device control or successful third-party uploads.
"""
from pathlib import Path
import unittest

ROOT=Path("android/app/src/main/java/ai/wolf/autopilot")


def java(name):
    return (ROOT / f"{name}.java").read_text(encoding="utf-8")


class WOLF12AssistedAutonomy(unittest.TestCase):
    def test_opt_in_service_os_binding_and_explicit_description(self):
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        xml=Path("android/app/src/main/res/xml/wolf_accessibility_service.xml").read_text("utf-8")
        policy=java("WolfAccessibilityService")
        self.assertIn('android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"', manifest)
        self.assertIn('android:resource="@xml/wolf_accessibility_service"', manifest)
        self.assertIn('android:canRetrieveWindowContent="true"', xml)
        self.assertNotIn("canTakeScreenshot", xml)
        self.assertNotIn("flagRetrieveInteractiveWindows", xml)
        self.assertNotIn("FLAG_REQUEST_FILTER_KEY_EVENTS", policy)
        self.assertNotIn("AccessibilityNodeInfo.ACTION_SET_TEXT", policy)
        self.assertNotIn("dispatchGesture", policy)
        self.assertIn("SystemClock.elapsedRealtime() + LIFETIME_MS", policy)
        self.assertIn("pending = null;", policy)
        self.assertIn("MAX_NODES", policy)
        self.assertIn("onInterrupt()", policy)

    def test_chat_requires_one_confirmation(self):
        router=java("WolfCommandRouter")
        home=java("HomeActivity")
        self.assertIn("UI_AUTOMATION", router)
        self.assertIn("case UI_AUTOMATION:", home)
        self.assertIn("handleAssistive(command)", home)
        self.assertIn('"Разрешить одно действие?"', home)
        self.assertIn('"Разрешить маршрут из кнопок?"', home)
        self.assertIn("WolfAccessibilityService.arm(", home)
        self.assertIn("Settings.ACTION_ACCESSIBILITY_SETTINGS", home)
        self.assertIn('setNegativeButton("Отмена", null)', home)

    def test_authenticated_web_navigation_only_one_safe_click(self):
        browser=java("BrowserActivity")
        script=java("BrowserActionPolicy")
        self.assertIn("showAssistedNavigation()", browser)
        self.assertIn("page.evaluateJavascript(BrowserActionPolicy.script(", browser)
        self.assertIn("page.evaluateJavascript(BrowserActionPolicy.script(", browser)
        self.assertIn('"Подтверди один клик"', browser)
        self.assertIn("!javascriptEnabled", browser)
        self.assertIn("BrowserActionPolicy.safeOrigin(page.getUrl())", browser)
        self.assertIn("location.origin", script)
        self.assertIn("matches.length!==1", script)
        self.assertIn("closest('form')", script)
        self.assertIn("matches[0].click()", script)
        self.assertIn("BrowserFlowPlan.parse(", browser)
        self.assertIn("runWorkflowStep(labels, origin", browser)
        self.assertIn("flowGeneration++", browser)
        self.assertNotIn("document.cookie", script)
        self.assertNotIn("localStorage", script)
        self.assertNotIn("addJavascriptInterface", browser)

    def test_no_permissions_for_full_device_control_or_root(self):
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        for banned in ("QUERY_ALL_PACKAGES", "READ_SMS", "READ_CONTACTS",
                       "MANAGE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW"):
            self.assertNotIn(banned, manifest)
        service=java("WolfAccessibilityService")
        self.assertNotIn("Runtime.getRuntime().exec", service)
        self.assertNotIn("getText().toString()", service)
        self.assertIn("!node.isPassword()", service)
        self.assertIn("AssistivePolicy.allowedPackage(app, getPackageName())", service)

    def test_actual_jvm_release_gate(self):
        workflow=Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("AssistedNavigationTest.java", workflow)
        self.assertIn("ai.wolf.autopilot.AssistedNavigationTest", workflow)
        gradle=Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.1.0'", gradle)


if __name__ == "__main__":
    unittest.main()
