"""WOLF 8.0 protection contracts: offline PDF/DOCX and portable authenticated archives.

These are source-level release checks. Real Android document handling,
cryptographic round-trips and phone RAM performance still need device tests.
"""
from pathlib import Path
import unittest

JAVA = Path("android/app/src/main/java/ai/wolf/autopilot")


class DocumentV8Contracts(unittest.TestCase):
    def test_pdfbox_offline_library_and_limits(self):
        gradle = Path("android/app/build.gradle").read_text("utf-8")
        extractor = (JAVA / "DocumentExtractor.java").read_text("utf-8")
        self.assertIn("com.tom-roush:pdfbox-android:2.0.27.0", gradle)
        self.assertRegex(gradle, r"versionName '[0-9]+[.][0-9]+[.][0-9]+'")
        self.assertIn("PDFBoxResourceLoader.init(context.getApplicationContext())", extractor)
        self.assertIn("PDDocument.load(data)", extractor)
        self.assertIn("PDFTextStripper", extractor)
        self.assertIn("MAX_PDF_BYTES = 4_000_000", extractor)
        self.assertIn("MAX_PDF_PAGES = 45", extractor)
        self.assertIn("MAX_TEXT_BYTES = 1_500_000", extractor)
        self.assertIn("document.isEncrypted()", extractor)
        self.assertNotIn("HttpURLConnection", extractor)
        self.assertNotIn("https://", extractor)

    def test_docx_rejects_zip_bomb_and_external_entities(self):
        extractor = (JAVA / "DocumentExtractor.java").read_text("utf-8")
        self.assertIn('"word/document.xml".equals(entryName)', extractor)
        self.assertIn("ZipInputStream", extractor)
        self.assertIn("entries > 180", extractor)
        self.assertIn("skipped > 2_000_000", extractor)
        self.assertIn('raw.contains("<!DOCTYPE")', extractor)
        self.assertIn('raw.contains("<!ENTITY")', extractor)
        self.assertIn("XmlPullParser", extractor)
        self.assertNotIn("Runtime.getRuntime().exec", extractor)

    def test_knowledge_base_uses_transactional_restore_and_retrieval(self):
        db = (JAVA / "KnowledgeBase.java").read_text("utf-8")
        self.assertIn('"pdf", "docx"', db)
        self.assertIn("DocumentExtractor.extract(context, name, input)", db)
        self.assertIn("db.beginTransaction()", db)
        self.assertIn("db.setTransactionSuccessful()", db)
        self.assertIn("restorePortableJson", db)
        self.assertIn("writePortableJson", db)
        self.assertIn("MAX_DOCS", db)
        self.assertIn("MAX_CHUNKS", db)
        self.assertIn("WHERE passages MATCH ? LIMIT 65", db)
        self.assertIn("getContentResolver().openInputStream(source)", db)

    def test_crypto_authenticates_before_any_database_change(self):
        encryption = (JAVA / "WolfKnowledgeBackup.java").read_text("utf-8")
        self.assertIn('Cipher.getInstance("AES/GCM/NoPadding")', encryption)
        self.assertIn('SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")', encryption)
        self.assertIn("PBKDF2_ITERATIONS = 240_000", encryption)
        self.assertIn("new SecureRandom()", encryption)
        self.assertIn("GCMParameterSpec(128, iv)", encryption)
        self.assertIn("new CipherInputStream(raw, cipher)", encryption)
        self.assertIn("while ((n = decrypted.read(buffer)) != -1)", encryption)
        self.assertLess(encryption.index("while ((n = decrypted.read(buffer)) != -1)"),
                        encryption.index("db.restorePortableJson(plain)"))
        self.assertIn("zip.closeEntry(); // ZIP CRC check", encryption)
        self.assertIn("stagingZip.delete()", encryption)
        self.assertIn("stagingJson.delete()", encryption)
        self.assertIn("Arrays.fill(passphrase, '\\0')", encryption)
        self.assertNotIn("GitHub", encryption)
        self.assertNotIn("HttpURLConnection", encryption)

    def test_android_requires_file_pickers_and_confirmation(self):
        ui = (JAVA / "MainActivity.java").read_text("utf-8")
        self.assertIn("EXPORT_KNOWLEDGE_ENCRYPTED", ui)
        self.assertIn("IMPORT_KNOWLEDGE_ENCRYPTED", ui)
        self.assertIn("WolfKnowledgeBackup.exportEncrypted", ui)
        self.assertIn("WolfKnowledgeBackup.restoreEncrypted", ui)
        self.assertIn('setTitle("Заменить текущую библиотеку?")', ui)
        self.assertIn('setPositiveButton("Проверить и восстановить"', ui)
        self.assertIn("temporaryKnowledgePassword = null", ui)
        self.assertIn("ACTION_OPEN_DOCUMENT", ui)
        self.assertIn("ACTION_CREATE_DOCUMENT", ui)
        self.assertIn("ЗАШИФРОВАННУЮ", ui)
        self.assertIn("PDF и DOCX с текстовым слоем", ui)


if __name__ == "__main__":
    unittest.main()
