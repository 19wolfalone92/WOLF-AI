"""WOLF AI 11.1 foreground-only native voice turn contracts."""
from pathlib import Path
import unittest

BASE = Path("android/app/src/main/java/ai/wolf/autopilot")


class ForegroundVoiceV111(unittest.TestCase):
    def test_permission_is_declared_and_only_requested_in_foreground(self):
        manifest = Path("android/app/src/main/AndroidManifest.xml").read_text("utf-8")
        home = (BASE / "HomeActivity.java").read_text("utf-8")
        self.assertIn("android.permission.RECORD_AUDIO", manifest)
        self.assertIn("VOICE_MIC_PERMISSION", home)
        self.assertIn("requestPermissions(", home)
        self.assertIn("onRequestPermissionsResult", home)
        self.assertIn("startVoiceTurn()", home)
        self.assertIn("inAppVoice.start()", home)
        self.assertIn("onStop()", home)
        self.assertIn("stopVoiceSession()", home)
        self.assertNotIn("android.permission.FOREGROUND_SERVICE_MICROPHONE", manifest)
        self.assertNotIn("startForegroundService", home)

    def test_android_recognizer_in_app_and_offline_preference(self):
        voice = (BASE / "InAppVoiceDialog.java").read_text("utf-8")
        for required in (
            "SpeechRecognizer.createOnDeviceSpeechRecognizer",
            "SpeechRecognizer.isOnDeviceRecognitionAvailable",
            "SpeechRecognizer.createSpeechRecognizer",
            "SpeechRecognizer.isRecognitionAvailable",
            "setRecognitionListener(",
            "RecognizerIntent.EXTRA_PREFER_OFFLINE",
            "onResults(Bundle results)",
            "onError(int code)",
            "recognizer.cancel()",
            "recognizer.destroy()",
        ):
            self.assertIn(required, voice, required)
        self.assertNotIn("MediaRecorder", voice)
        self.assertNotIn("AudioRecord", voice)
        self.assertNotIn("startService", voice)

    def test_turns_are_audio_input_then_tts_then_audio_not_parallel(self):
        home = (BASE / "HomeActivity.java").read_text("utf-8")
        tts = (BASE / "SpeechCenter.java").read_text("utf-8")
        self.assertIn("inAppVoice = new InAppVoiceDialog", home)
        self.assertIn("speaker.setOnUtteranceDone", home)
        self.assertIn("!busy && !speechListening", home)
        self.assertIn("onRecognized(String recognized)", home)
        self.assertIn("sendCurrent()", home)
        self.assertIn("inAppVoice.stop()", home)
        self.assertIn("onFailure(int code)", home)
        self.assertIn("UtteranceProgressListener", tts)
        self.assertIn("speaker.stop()", home)
        self.assertIn("RecognizerIntent.ACTION_RECOGNIZE_SPEECH", home)

    def test_version_and_explanatory_documents(self):
        gradle = Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '13.0.0'", gradle)
        guide = (BASE / "GuideActivity.java").read_text("utf-8")
        self.assertIn("Диалог голосом", guide)


if __name__ == "__main__":
    unittest.main()
