"""Screenshot regression: WOLF must respond instantly to common prompts and speak on demand."""
from pathlib import Path
import unittest

ROOT=Path("android/app/src/main/java/ai/wolf/autopilot")
def java(n):return (ROOT/(n+".java")).read_text("utf-8")

class UserVisibleResponseAndVoice(unittest.TestCase):
    def test_question_mark_and_can_you_do_are_not_gguf_calls(self):
        router=java("WolfCommandRouter")
        home=java("HomeActivity")
        quick=java("WolfHelp")
        self.assertIn('"что ты можешь"',router)
        self.assertIn('Kind.SHORT_PROMPT',router)
        self.assertIn('Kind.RETRY_PREVIOUS',router)
        self.assertIn('case HELP:',home)
        self.assertIn('reply(WolfHelp.capabilities());',home)
        self.assertIn('case SHORT_PROMPT:',home)
        self.assertIn('reply(WolfHelp.promptForEmpty());',home)
        self.assertIn('case RETRY_PREVIOUS:',home)
        self.assertIn('retryPreviousQuestion();',home)
        self.assertIn('if(kind==WolfCommandRouter.Kind.HELP)',home)
        self.assertIn('if(kind==WolfCommandRouter.Kind.CHAT)',home)
        self.assertIn('Финальную публикацию',quick)
        self.assertIn('без обязательной подписки',quick)
        self.assertNotIn('EmbeddedAi.',quick)
        self.assertIn('TikTok Studio',quick)

    def test_speech_test_and_toggle_always_audible(self):
        home=java("HomeActivity")
        tts=java("SpeechCenter")
        self.assertIn('"🔊 Проверить голос"',home)
        self.assertIn('"🔊 Проверить голос WOLF (тест Android TTS)"',home)
        self.assertIn('case 14: testSpeech(); break;',home)
        self.assertIn('String result = speaker.speak(',home)
        self.assertIn('speaker.setStatusListener(',home)
        self.assertIn('setStatusListener(StatusListener callback)',tts)
        self.assertIn('notifyStatus("🔈',tts)
        self.assertIn('showVoiceSettings(',home)
        self.assertIn('android.settings.TTS_SETTINGS',home)
        self.assertIn('voiceAnswer.setText(aloud ? ',home)
        self.assertIn('getBoolean("read_replies_aloud",false)',home)
        self.assertIn('putBoolean("read_replies_aloud", aloud)',home)

    def test_one_shot_microphone_speaks_even_if_global_voice_off(self):
        home=java("HomeActivity")
        self.assertIn('speakOnce = true;',home)
        self.assertIn('aloud || voiceSession || speakOnce',home)
        self.assertIn('speakOnce = false;',home)
        self.assertIn('RecognizerIntent.ACTION_RECOGNIZE_SPEECH',home)
        self.assertIn('onActivityResult(int code, int result',home)
        self.assertIn('setOnUtteranceDone(',home)
        self.assertIn('onStop()',home)

    def test_speech_error_is_visible_and_recovery_not_silent(self):
        home=java("HomeActivity")
        tts=java("SpeechCenter")
        self.assertIn('if (isTtsError(outcome))',home)
        self.assertIn('subtitle.setText("🔈 "+outcome)',home)
        self.assertIn('notifyStatus(problem);',tts)
        self.assertIn('status != TextToSpeech.SUCCESS',tts)
        self.assertIn('onError(String utteranceId)',tts)
        self.assertIn('queue',tts.lower())
        manifest=Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        self.assertIn('android.permission.RECORD_AUDIO',manifest)
        self.assertNotIn('android.permission.FOREGROUND_SERVICE_MICROPHONE',manifest)

    def test_version_16_1(self):
        version=Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.3.0'",version)
        self.assertIn("versionCode 29",version)
        workflow=Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("WolfResponseRecoveryTest.java",workflow)

if __name__=="__main__":
    unittest.main()
