"""Private signed-in local Playwright sessions must never leave owner's PC."""
from __future__ import annotations

import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from wolf_ai.cloud_browser import BrowserSafetyError
from wolf_ai.private_browser import assert_same_origin, profile_directory, prohibit_ci


class PrivateSessionContracts(unittest.TestCase):
    def test_ci_rejected_even_with_profile(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true"}):
            with self.assertRaises(BrowserSafetyError):
                prohibit_ci()

    def test_local_profile_is_private_and_not_named_from_path(self):
        with tempfile.TemporaryDirectory() as d:
            with patch.object(Path, "home", return_value=Path(d)):
                folder = profile_directory("my-site")
                self.assertTrue(folder.is_dir())
                self.assertTrue(str(folder).startswith(d))
                if os.name == "posix":
                    self.assertEqual(folder.stat().st_mode & 0o777, 0o700)
                for name in ("../secrets", "A", "spaces in name", "/tmp/override"):
                    with self.subTest(name=name):
                        with self.assertRaises(BrowserSafetyError):
                            profile_directory(name)

    def test_no_change_of_authenticated_origin(self):
        assert_same_origin("https://example.com/app","https://example.com/home")
        for bad in (
            "http://example.com/app", "https://another.example.com/app",
            "https://example.com:443/app"
        ):
            with self.subTest(url=bad), self.assertRaises(BrowserSafetyError):
                assert_same_origin("https://example.com/app",bad)

    def test_private_helper_does_not_write_auth_to_git_or_cloud(self):
        source=Path("wolf_ai/private_browser.py").read_text("utf-8")
        self.assertIn("launch_persistent_context",source)
        self.assertIn("headless=False",source)
        self.assertIn("prohibit_ci()",source)
        self.assertIn("accept_downloads=False",source)
        self.assertNotIn("storage_state(",source)
        self.assertNotIn("add_cookies(",source)
        self.assertNotIn("actions/upload-artifact",source)
        self.assertNotIn("print(page.content(",source)
        self.assertNotIn("page.screenshot(",source)
        self.assertNotIn("requests.post(",source)


if __name__ == "__main__":
    unittest.main()
