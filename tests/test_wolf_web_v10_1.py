"""WOLF 10.1 private, evidence-first web search regression contracts."""

import unittest
from pathlib import Path

ROOT = Path("android/app/src/main/java/ai/wolf/autopilot")


class SearchContracts(unittest.TestCase):
    def test_real_search_is_in_primary_chat_not_just_browser(self):
        home = (ROOT / "HomeActivity.java").read_text("utf-8")
        self.assertIn("case WEB_SEARCH:", home)
        self.assertIn("researchWeb(decision.argument)", home)
        self.assertIn("WebSearchService.search(query)", home)
        self.assertIn("results.links()", home)
        self.assertIn("EmbeddedAi.ask(getApplicationContext(), prompt", home)
        self.assertIn("results.evidence()", home)
        self.assertIn("поисковые фрагменты", home)
        self.assertIn(
            "if (decision.argument == null || decision.argument.trim().isEmpty())",
            home,
        )

    def test_search_only_sends_explicit_phrase_to_provider(self):
        source = (ROOT / "WebSearchService.java").read_text("utf-8")
        self.assertIn('"https://html.duckduckgo.com/html/?q="', source)
        self.assertIn('URLEncoder.encode(query, "UTF-8")', source)
        self.assertIn('connection.setRequestMethod("GET")', source)
        self.assertIn("connection.setInstanceFollowRedirects(false)", source)
        self.assertIn("out.size() + n > MAX_HTML", source)
        self.assertIn("found.isEmpty()", source)
        self.assertIn("results.size()", source)
        self.assertNotIn('setRequestProperty("Authorization"', source)
        self.assertNotIn("chat.recent", source)
        self.assertNotIn("CookieManager", source)
        self.assertNotIn("HttpsURLConnection.connect(", source)

    def test_real_jvm_search_parser_in_ci(self):
        workflow = Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("WebSearchServiceTest.java", workflow)
        self.assertIn("ai.wolf.autopilot.WebSearchServiceTest", workflow)
        self.assertIn("javac", workflow)
        test = Path("tests/java/WebSearchServiceTest.java").read_text("utf-8")
        for part in (
            "bot challenge",
            "must unwrap DuckDuckGo",
            "no insecure or executable",
        ):
            self.assertIn(part, test)

    def test_version_and_russian_guide_updated(self):
        gradle = Path("android/app/build.gradle").read_text("utf-8")
        self.assertIn("versionName '17.1.0'", gradle)
        guide = Path("docs/WOLF_CHAT_PHONE_10_RU.md").read_text("utf-8")
        self.assertIn("Дополнение 10.1", guide)
        self.assertIn("поисковые фрагменты", guide)


if __name__ == "__main__":
    unittest.main()
