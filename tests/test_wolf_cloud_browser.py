"""WOLF public cloud browser safety rules, executable without Playwright."""
import unittest
from unittest.mock import patch

from wolf_ai.cloud_browser import (
    BrowserSafetyError,
    parse_steps,
    safe_click_js,
    validate_dns,
    validate_url,
)


class CloudBrowserSafetyTests(unittest.TestCase):
    def test_public_url_only(self):
        self.assertEqual(validate_url("https://example.com/path"), "https://example.com/path")
        for value in (
            "http://example.com",
            "file:///etc/passwd",
            "https://127.0.0.1/internal",
            "https://localhost/internal",
            "https://private.local/path",
            "https://metadata.google.internal/latest",
            "https://example.com:443",
            "https://name:password@example.com/",
            "https://example.com/?access_token=abc",
            "https://example.com/path#frag",
            "https://example..com/path",
        ):
            with self.subTest(value=value), self.assertRaises(BrowserSafetyError):
                validate_url(value)

    def test_exact_navigation_labels_are_bounded(self):
        self.assertEqual(parse_steps("Профиль > Настройки"), ["Профиль", "Настройки"])
        self.assertEqual(parse_steps(""), [])
        for value in (
            "Оплатить", "Удалить аккаунт", "Publish now",
            "Профиль > Профиль", "A", "Профиль > Настройки > Помощь > Детали > Обзор",
        ):
            with self.subTest(value=value), self.assertRaises(BrowserSafetyError):
                parse_steps(value)

    def test_rendered_js_is_fixed_origin_non_form_action(self):
        script = safe_click_js("https://example.com", 'Профиль"; alert(1); //')
        self.assertIn("location.origin!==origin", script)
        self.assertIn("closest('form')", script)
        self.assertIn("matches.length!==1", script)
        self.assertIn("matches[0].click()", script)
        self.assertNotIn("document.cookie", script)
        self.assertNotIn("localStorage", script)
        self.assertNotIn("eval(", script)
        self.assertIn("\\\"; alert(1); //", script)

    def test_dns_must_be_global(self):
        with patch("wolf_ai.cloud_browser.socket.getaddrinfo") as mock:
            mock.return_value = [
                (2, 1, 6, "", ("8.8.8.8", 443)),
            ]
            validate_dns("example.com")
            mock.return_value = [
                (2, 1, 6, "", ("10.0.0.1", 443)),
            ]
            with self.assertRaises(BrowserSafetyError):
                validate_dns("example.com")


if __name__ == "__main__":
    unittest.main()
