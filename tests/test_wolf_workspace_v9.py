"""WOLF AI 9 — portable personal workspace encrypted backup release gates.

Structural/contract tests do NOT replace an Android device roundtrip test.
"""
from pathlib import Path
import unittest

SOURCE = Path("android/app/src/main/java/ai/wolf/autopilot")


def java(name):
    return (SOURCE / (name + ".java")).read_text("utf-8")


class PortableWorkspaceContracts(unittest.TestCase):
    def test_all_four_data_types_roundtrip_without_keystore_keys(self):
        backup = java("WolfWorkspaceBackup")
        self.assertIn('NAMES = {', backup)
        for name in ["library.json", "learning.json", "chat.json", "tasks.json"]:
            self.assertIn('"' + name + '"', backup)
        self.assertIn('documents.writePortableJson(zip)', backup)
        self.assertIn('learning.exportJson()', backup)
        self.assertIn('chat.exportJson()', backup)
        self.assertIn('tasks.exportJson()', backup)
        self.assertIn('learning.importJson(newLearning)', backup)
        self.assertIn('chat.importJson(newChat)', backup)
        self.assertIn('tasks.importJson(newTasks)', backup)
        self.assertIn('documents.restorePortableJson(text)', backup)
        self.assertNotIn('secrets.load()', backup)
        self.assertNotIn('SecureStore', backup)
        self.assertNotIn('MetricoolOAuth', backup)
        self.assertNotIn('getSharedPreferences(', backup)

    def test_aes_authenticated_and_all_components_validated_first(self):
        backup = java("WolfWorkspaceBackup")
        implementation = java("WorkspaceCrypto")
        self.assertIn("WOLFWK9", implementation)
        self.assertIn('Cipher.getInstance("AES/GCM/NoPadding")', implementation)
        self.assertIn("PBKDF2WithHmacSHA256", implementation)
        self.assertIn("ITERATIONS = 240_000", implementation)
        self.assertIn("new SecureRandom()", backup)
        self.assertIn("GCMParameterSpec(128, iv)", implementation)
        self.assertIn("result.updateAAD(MAGIC)", implementation)
        self.assertIn("WorkspaceCrypto.cipher(", backup)
        self.assertIn("Cipher.ENCRYPT_MODE, passphrase, salt, iv", backup)
        self.assertIn("Cipher.DECRYPT_MODE, passphrase, salt, iv", backup)
        self.assertIn("CipherInputStream(sourceInput, cipher)", backup)
        self.assertIn("while ((read = decrypted.read(bytes)) != -1)", backup)
        self.assertIn("zip.closeEntry(); // Triggers CRC validation", backup)
        self.assertIn("MAX_ZIP = 65_000_000", backup)
        self.assertIn("MAX_LIBRARY_JSON = 55_000_000", backup)
        validation = backup.index("TaskStore.validatePortable(newTasks)")
        mutation = backup.index("documents.restorePortableJson(text)")
        self.assertLess(validation, mutation)
        self.assertLess(backup.index("oldTasks = tasks.exportJson()"), mutation)
        self.assertIn("changingDatabase = true", backup)
        self.assertIn("oldLibrary", backup)
        self.assertIn("rollbackOk = false", backup)
        self.assertIn("ciphertext.delete()", backup)
        self.assertIn("library.delete()", backup)
        self.assertIn("oldLibrary.delete()", backup)
        self.assertIn("Arrays.fill(passphrase, '\\0')", backup)

    def test_java_runtime_crypto_suite_is_executed_in_android_ci(self):
        workflow = Path(".github/workflows/wolf-ai-android.yml").read_text("utf-8")
        self.assertIn("WorkspaceCryptoTest.java", workflow)
        self.assertIn("ai.wolf.autopilot.WorkspaceCryptoTest", workflow)
        self.assertIn("javac", workflow)

    def test_chat_backup_bounded_and_transactional(self):
        store = java("ChatStore")
        self.assertIn("public String exportJson()", store)
        self.assertIn("public static void validatePortable", store)
        self.assertIn('SELECT role,body,created FROM messages', store)
        self.assertIn("values.length() > 40", store)
        self.assertIn("body.length() > 6000", store)
        self.assertIn("db.beginTransaction()", store)
        self.assertIn("db.setTransactionSuccessful()", store)
        self.assertIn('db.delete("messages", null, null)', store)

    def test_tasks_portable_without_auto_resubmission(self):
        store = java("TaskStore")
        self.assertIn('task_uid', store)
        self.assertIn('agent_status', store)
        self.assertIn('agent_submitted_at', store)
        self.assertIn("public static void validatePortable", store)
        self.assertIn("tasks.length() > 100", store)
        self.assertIn("unique.add(uid)", store)
        self.assertIn("db.beginTransaction()", store)
        self.assertIn("db.setTransactionSuccessful()", store)
        # Only DB rows restored. No workflow_dispatch in import or
        # task backup/export code.
        method = store.split("public void importJson(", 1)[1]
        self.assertNotIn("GithubApi.dispatch", method)
        self.assertNotIn("dispatchAgent(", method)

    def test_learning_validates_without_mutating_and_import_prechecks(self):
        source = java("LearningStore")
        self.assertIn("public static void validatePortable", source)
        self.assertIn("validatePortable(content);", source)
        self.assertIn("db.beginTransaction()", source)
        self.assertIn("db.setTransactionSuccessful()", source)

    def test_android_asks_owner_before_export_and_replacement(self):
        screen = java("MainActivity")
        for code in ("EXPORT_WORKSPACE_ENCRYPTED", "IMPORT_WORKSPACE_ENCRYPTED"):
            self.assertIn(code, screen)
        self.assertIn('wolf-workspace.wolfpack', screen)
        self.assertIn("chooseKnowledgePassword(true, true)", screen)
        self.assertIn("chooseKnowledgePassword(false, true)", screen)
        self.assertIn('setTitle("Заменить личные данные из .wolfpack?")', screen)
        self.assertIn('setPositiveButton("Проверить и восстановить"', screen)
        self.assertIn("WolfWorkspaceBackup.exportEncrypted(", screen)
        self.assertIn("WolfWorkspaceBackup.restoreEncrypted(", screen)
        self.assertIn("temporaryKnowledgePassword = null", screen)
        self.assertIn("renderTasks()", screen)
        gradle = Path("android/app/build.gradle").read_text("utf-8")
        self.assertRegex(gradle, "versionName '[0-9]+[.][0-9]+[.][0-9]+'")


if __name__ == "__main__":
    unittest.main()
