"""Release gates for private document-RAG and allowlisted GitHub inspection.

These are structural safety contracts; device SQLite FTS4 and the system
file-picker require additional real-Android acceptance tests.
"""
from pathlib import Path
import unittest

JAVA = Path("android/app/src/main/java/ai/wolf/autopilot")


class KnowledgeReleaseContracts(unittest.TestCase):
    def test_document_ingestion_is_opt_in_private_and_limited(self):
        source = (JAVA / "KnowledgeBase.java").read_text("utf-8")
        ui = (JAVA / "MainActivity.java").read_text("utf-8")
        self.assertIn("SQLiteOpenHelper", source)
        self.assertIn('wolf_documents.db', source)
        self.assertIn("FTS4", source.upper())
        self.assertIn("tokenize=unicode61", source)
        self.assertIn("MAX_DOCS = 30", source)
        self.assertIn("MAX_BYTES = 1_500_000", source)
        self.assertIn("MAX_CHUNKS = 1900", source)
        self.assertIn("onMalformedInput(CodingErrorAction.REPORT)", source)
        self.assertIn("getContentResolver().openInputStream(source)", source)
        self.assertNotIn("HttpURLConnection", source)
        self.assertNotIn("github.com", source.lower())
        self.assertIn("ACTION_OPEN_DOCUMENT", ui)
        self.assertIn("IMPORT_DOCUMENT", ui)
        self.assertIn('setPositiveButton("Индексировать локально"', ui)
        self.assertIn("knowledge.importDocument", ui)

    def test_retrieval_is_bound_unicode_and_has_real_citations(self):
        source = (JAVA / "KnowledgeBase.java").read_text("utf-8")
        answer = (JAVA / "DocumentAssistant.java").read_text("utf-8")
        self.assertIn("WHERE passages MATCH ? LIMIT 65", source)
        self.assertIn('new String[]{match.toString()}', source)
        self.assertIn('return "[" + source + " · фрагмент " + segment + "]"', source)
        self.assertIn("Копия хранится", (JAVA / "MainActivity.java").read_text("utf-8"))
        self.assertIn("EmbeddedAi.ask(app, request, history, facts)", answer)
        self.assertIn("Наличие фрагмента не доказывает", answer)
        self.assertIn("documents.search(question, 3)", answer)
        self.assertIn("knowledge.deleteDocument", (JAVA / "MainActivity.java").read_text("utf-8"))
        self.assertIn("knowledge.clearAll", (JAVA / "MainActivity.java").read_text("utf-8"))

    def test_agent_uses_local_sources_without_uploading_notes(self):
        source = (JAVA / "LocalToolAgent.java").read_text("utf-8")
        self.assertIn("documents.search(question, 2)", source)
        self.assertIn("KnowledgeBase.context(excerpts, 800)", source)
        self.assertIn("EmbeddedAi.ask(app, task, chat, references)", source)
        self.assertIn("GithubRepoInspector.inspect(repository, token)", source)
        self.assertIn('context.toString().substring(0, Math.min(context.length(), 1300))', source)
        self.assertNotIn('Runtime.getRuntime().exec', source)
        # Generic bug-fixing prompts must not trigger an unrelated
        # Wikimedia search leaking potentially private description.
        self.assertNotIn('"wiki", "найди", "найти"', source)

    def test_github_repo_inspector_fixed_owner_and_read_only(self):
        source = (JAVA / "GithubRepoInspector.java").read_text("utf-8")
        self.assertIn('OWNER = "19wolfalone92"', source)
        self.assertIn('"WOLF-AI", "Two-Worlds-Studio", "Shaira"', source)
        self.assertIn('!permitted(repoName)', source)
        self.assertIn('con.setRequestMethod("GET")', source)
        self.assertIn("GitHub source inspection HTTP", source)
        self.assertIn("?recursive=1", source)
        self.assertNotIn("con.setRequestMethod(\"POST\")", source)
        self.assertNotIn("con.setDoOutput(true)", source)
        self.assertIn("SAFE_REF.matcher(branch)", source)
        self.assertIn("decode(readme.optString", source)


if __name__ == "__main__":
    unittest.main()
