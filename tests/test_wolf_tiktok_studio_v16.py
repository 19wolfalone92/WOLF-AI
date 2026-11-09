"""Actual TikTok Studio policy tests. Never start browser or upload videos."""
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from wolf_ai.cloud_browser import BrowserSafetyError
from wolf_ai.tiktok_studio import (
    STUDIO_URL, MAX_CAPTION, assert_studio, valid_video,
    valid_caption, prohibit_ci,
)


class TikTokStudioSafetyTests(unittest.TestCase):
    def test_only_official_studio_site(self):
        assert_studio(STUDIO_URL)
        assert_studio("https://www.tiktok.com/tiktokstudio/upload?from=creator_center")
        for value in (
            "https://www.tiktok.com.evil.invalid/tiktokstudio/upload",
            "https://evil.invalid/tiktokstudio/upload",
            "http://www.tiktok.com/tiktokstudio/upload",
            "https://www.tiktok.com:444/tiktokstudio/upload",
            "https://www.tiktok.com/settings",
        ):
            with self.subTest(url=value), self.assertRaises(BrowserSafetyError):
                assert_studio(value)

    def test_ci_cannot_open_private_account(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true"}):
            with self.assertRaises(BrowserSafetyError):
                prohibit_ci()

    def test_local_mp4_signature_and_size(self):
        with tempfile.TemporaryDirectory() as folder:
            p=Path(folder)/"test.mp4"
            p.write_bytes(b"\x00\x00\x00\x18ftypisom"+b"0"*2048)
            self.assertEqual(valid_video(str(p)),p)
            p.write_bytes(b"NOT_MP4"*500)
            with self.assertRaises(BrowserSafetyError):
                valid_video(str(p))
            p.rename(Path(folder)/"test.txt")
            with self.assertRaises((BrowserSafetyError,FileNotFoundError)):
                valid_video(str(p))

    def test_caption_length_and_control_char(self):
        self.assertEqual(valid_caption("  #storyglitch  "), "#storyglitch")
        self.assertEqual(len(valid_caption("a"*MAX_CAPTION)), MAX_CAPTION)
        with self.assertRaises(BrowserSafetyError):
            valid_caption("a"*(MAX_CAPTION+1))
        with self.assertRaises(BrowserSafetyError):
            valid_caption("a\x00b")

    def test_consent_gates_and_no_session_export(self):
        src=Path("wolf_ai/tiktok_studio.py").read_text("utf-8")
        self.assertIn("launch_persistent_context(",src)
        self.assertIn("headless=False",src)
        self.assertIn("prohibit_ci()",src)
        self.assertIn("UPLOAD ",src)
        self.assertIn("PUBLISH ",src)
        self.assertIn("set_input_files(",src)
        self.assertIn("assert_studio(page.url)",src)
        self.assertIn("Publish is NOT verified",src)
        self.assertNotIn("storage_state(",src)
        self.assertNotIn("add_cookies(",src)
        self.assertNotIn("page.screenshot(",src)
        self.assertNotIn("requests.post(",src)


if __name__=="__main__":
    unittest.main()
