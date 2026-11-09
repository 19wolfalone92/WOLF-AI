"""WOLF 16 Android source contracts and bounded depth/TikTok Studio UI."""
from pathlib import Path
import unittest

ROOT=Path("android/app/src/main/java/ai/wolf/autopilot")

def source(name):
    return (ROOT/(name+".java")).read_text("utf-8")

class DepthAndStudioContracts(unittest.TestCase):
    def test_depth_preferences_and_real_passes(self):
        c=source("ThinkingDepth")
        ui=source("DepthSettingsActivity")
        infer=source("EmbeddedAi")
        grounded=source("DocumentAssistant")
        thinker=source("OfflineThinker")
        self.assertIn('PREFS="wolf_depth_v16"',c)
        self.assertIn("MAX_STEPS=4",c)
        self.assertIn("MAX_TOKENS=768",c)
        self.assertIn('p.getBoolean("automatic",false)',c)
        self.assertIn('putInt("steps",settings.steps)',c)
        self.assertIn('putInt("tokens",settings.tokens)',c)
        self.assertIn('new SeekBar(this)',ui)
        self.assertIn('setMax(3)',ui)
        self.assertIn('setMax(10)',ui)
        self.assertIn('ThinkingDepth.save(this',ui)
        self.assertIn('automatic.isChecked()',ui)
        self.assertIn('int outputTokens',infer)
        self.assertIn('ThinkingDepth.clampTokens(outputTokens)',infer)
        self.assertIn('int maxTokens',grounded)
        self.assertIn('DocumentAssistant.ask(',thinker)
        self.assertIn('if(stages>=2)',thinker)
        self.assertIn('if(stages>=3)',thinker)
        self.assertIn('if(stages>=4)',thinker)
        self.assertIn('ThinkingDepth.clampSteps(depth.steps)',thinker)
        self.assertEqual(thinker.count('EmbeddedAi.ask('),3)

    def test_depth_chat_commands_and_no_auto_default(self):
        router=source("WolfCommandRouter")
        home=source("HomeActivity")
        self.assertIn('DEPTH_SETTINGS',router)
        self.assertIn('case DEPTH_SETTINGS:',home)
        self.assertIn('ThinkingDepth.updateSteps(',home)
        self.assertIn('openDepthSettings()',home)
        self.assertIn('ThinkingDepth.load(this).automatic',home)
        self.assertIn('OfflineThinker.run(',home)
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn('DepthSettingsActivity',manifest)

    def test_tiktok_is_real_user_scoped_media_flow_not_fake_post(self):
        studio=source("TikTokStudioActivity")
        browser=source("BrowserActivity")
        home=source("HomeActivity")
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn('TikTokStudioActivity',manifest)
        self.assertIn('PublicationCenter.pickIntent()',studio)
        self.assertIn('PublicationCenter.inspect(this,data.getData())',studio)
        self.assertIn('selected.openingBrightness',studio)
        self.assertIn('PublicationCenter.share(this,selected,',studio)
        self.assertIn('new Intent(Intent.ACTION_VIEW,Uri.parse(STUDIO))',studio)
        self.assertIn('BrowserActivity.EXTRA_TARGET',studio)
        self.assertIn('onShowFileChooser(',browser)
        self.assertIn('STUDIO_VIDEO_PICK',browser)
        self.assertIn('new Intent(Intent.ACTION_OPEN_DOCUMENT)',browser)
        self.assertIn('setType("video/mp4")',browser)
        self.assertIn('cancelStudioPicker()',browser)
        self.assertIn('isOfficialTikTokStudio()',browser)
        self.assertIn('case TIKTOK_STUDIO:',home)
        self.assertNotIn('MetricoolMcp',studio)
        self.assertNotIn('MetricoolOAuth',studio)
        self.assertNotIn('HfBrainClient',studio)
        self.assertNotIn('document.cookie',browser)

    def test_truthful_release_and_no_remote_privacy_leak(self):
        gradle=Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.0.0'",gradle)
        self.assertIn("versionCode 26",gradle)
        guide=source("GuideActivity")
        self.assertIn("настраиваемая глубина",guide)
        self.assertIn("напрямую в TikTok Studio",guide)
        self.assertIn("НЕ подтверждена",guide)
        self.assertTrue(Path("docs/WOLF_CAPABILITIES_16_RU.md").exists())
        self.assertTrue(Path("docs/WOLF_TIKTOK_STUDIO_16_RU.md").exists())


if __name__=="__main__":
    unittest.main()
