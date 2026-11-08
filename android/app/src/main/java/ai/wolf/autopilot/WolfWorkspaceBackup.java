package ai.wolf.autopilot;

import android.content.Context;
import android.net.Uri;
import android.os.StatFs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

/**
 * WOLF 9.0 portable personal workspace snapshot (without credentials).
 *
 * Distinct authenticated format from WOLF 8 .wolfkb document-only backups.
 * All four JSON components are authenticated, decompressed and validated
 * BEFORE beginning restoration; each SQLite DB is transactional and a
 * best-effort rollback snapshot protects against cross-DB failure.
 *
 * WARNING: Cross-database SQLite transactions are NOT globally atomic.
 */
public final class WolfWorkspaceBackup {
    private static final byte[] MAGIC = WorkspaceCrypto.MAGIC;
    private static final int MAX_ZIP = 65_000_000;
    private static final int MAX_LIBRARY_JSON = 55_000_000;
    private static final String[] NAMES = {
            "library.json", "learning.json", "chat.json", "tasks.json"
    };
    private WolfWorkspaceBackup() {}

    private static void exact(InputStream in, byte[] target) throws IOException {
        int offset = 0;
        while (offset < target.length) {
            int read = in.read(target, offset, target.length - offset);
            if (read < 0) throw new IOException("Архив обрезан.");
            offset += read;
        }
    }

    private static void writeSmall(ZipOutputStream zip, String name,
                                   String text, int limit) throws Exception {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > limit)
            throw new IllegalArgumentException("Компонент архива слишком большой: " + name);
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    public static String exportEncrypted(Context context, KnowledgeBase documents,
                                         LearningStore learning, ChatStore chat,
                                         TaskStore tasks, Uri destination,
                                         char[] passphrase) throws Exception {
        if (destination == null || !"content".equals(destination.getScheme()))
            throw new IllegalArgumentException("Выбери место экспорта в Android Files.");
        // Fetch data before writing an output file to avoid leaving half-created
        // archives when local database reads or serialization fail.
        String learningJson = learning.exportJson();
        String chatJson = chat.exportJson();
        String taskJson = tasks.exportJson();
        LearningStore.validatePortable(learningJson);
        ChatStore.validatePortable(chatJson);
        TaskStore.validatePortable(taskJson);
        byte[] salt = new byte[16], iv = new byte[12];
        SecureRandom random = new SecureRandom();
        random.nextBytes(salt);
        random.nextBytes(iv);
        try {
            Cipher cipher = WorkspaceCrypto.cipher(
                    Cipher.ENCRYPT_MODE, passphrase, salt, iv);
            int documentCount;
            try (OutputStream output = context.getContentResolver()
                    .openOutputStream(destination, "w")) {
                if (output == null)
                    throw new IOException("Невозможно создать зашифрованный файл.");
                output.write(MAGIC);
                output.write(salt);
                output.write(iv);
                try (ZipOutputStream zip =
                             new ZipOutputStream(new CipherOutputStream(output, cipher))) {
                    zip.putNextEntry(new ZipEntry(NAMES[0]));
                    documentCount = documents.writePortableJson(zip);
                    zip.closeEntry();
                    writeSmall(zip, NAMES[1], learningJson, 150_000);
                    writeSmall(zip, NAMES[2], chatJson, 280_000);
                    writeSmall(zip, NAMES[3], taskJson, 200_000);
                    zip.finish();
                }
            }
            return "✅ Полная личная копия создана. Документов: " + documentCount +
                    ", сохранены также память, чат и журнал заданий.\n" +
                    "Токены, ключи, модели GGUF и APK НЕ экспортированы. " +
                    "Сохрани пароль отдельно от архива.";
        } finally {
            if (passphrase != null) Arrays.fill(passphrase, '\0');
        }
    }

    private static byte[] readZipEntry(ZipInputStream zip, String expected,
                                       int max, File largeFile) throws Exception {
        ZipEntry entry = zip.getNextEntry();
        if (entry == null || entry.isDirectory() || !expected.equals(entry.getName()))
            throw new IllegalArgumentException("Неверная структура полного архива WOLF.");
        try (OutputStream output = largeFile == null ?
                new ByteArrayOutputStream() : new FileOutputStream(largeFile)) {
            byte[] bytes = new byte[8192];
            int read;
            long total = 0;
            while ((read = zip.read(bytes)) != -1) {
                total += read;
                if (total > max)
                    throw new IllegalArgumentException("Компонент архива превышает лимит.");
                output.write(bytes, 0, read);
            }
            zip.closeEntry(); // Triggers CRC validation
            if (largeFile != null) return null;
            return ((ByteArrayOutputStream) output).toByteArray();
        }
    }

    public static String restoreEncrypted(Context context, KnowledgeBase documents,
                                          LearningStore learning, ChatStore chat,
                                          TaskStore tasks, Uri source,
                                          char[] passphrase) throws Exception {
        if (source == null || !"content".equals(source.getScheme()))
            throw new IllegalArgumentException("Выбери архив через Android Files.");
        File ciphertext = File.createTempFile("wolf9-workspace-", ".zip", context.getCacheDir());
        File library = File.createTempFile("wolf9-library-", ".json", context.getCacheDir());
        File oldLibrary = File.createTempFile("wolf9-rollback-", ".json", context.getCacheDir());
        boolean changingDatabase = false;
        String oldLearning = null, oldChat = null, oldTasks = null;
        try {
            long free = new StatFs(context.getCacheDir().getAbsolutePath()).getAvailableBytes();
            if (free < 190_000_000L)
                throw new IllegalStateException("Для безопасного восстановления " +
                        "требуется не менее 190 МБ свободного места.");
            try (InputStream sourceInput =
                         context.getContentResolver().openInputStream(source)) {
                if (sourceInput == null)
                    throw new IOException("Невозможно прочитать файл.");
                byte[] magic = new byte[MAGIC.length];
                exact(sourceInput, magic);
                if (!Arrays.equals(magic, MAGIC))
                    throw new IllegalArgumentException("Нужна полная копия WOLF 9 (.wolfpack), " +
                            "а не документная .wolfkb от WOLF 8.");
                byte[] salt = new byte[16], iv = new byte[12];
                exact(sourceInput, salt);
                exact(sourceInput, iv);
                Cipher cipher = WorkspaceCrypto.cipher(
                        Cipher.DECRYPT_MODE, passphrase, salt, iv);
                // AES-GCM tag is verified completely before any DB writes.
                try (CipherInputStream decrypted = new CipherInputStream(sourceInput, cipher);
                     FileOutputStream output = new FileOutputStream(ciphertext)) {
                    long bytesTotal = 0;
                    byte[] bytes = new byte[8192];
                    int read;
                    while ((read = decrypted.read(bytes)) != -1) {
                        bytesTotal += read;
                        if (bytesTotal > MAX_ZIP)
                            throw new IllegalArgumentException("Архив больше 65 МБ.");
                        output.write(bytes, 0, read);
                    }
                    output.getFD().sync();
                } catch (IOException error) {
                    throw new IllegalArgumentException(
                            "Пароль неверен или архив повреждён (AES-GCM).");
                }
            }
            byte[] learningData, chatData, taskData;
            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(ciphertext))) {
                readZipEntry(zip, NAMES[0], MAX_LIBRARY_JSON, library);
                learningData = readZipEntry(zip, NAMES[1], 150_000, null);
                chatData = readZipEntry(zip, NAMES[2], 280_000, null);
                taskData = readZipEntry(zip, NAMES[3], 200_000, null);
                if (zip.getNextEntry() != null)
                    throw new IllegalArgumentException("Архив содержит лишние файлы.");
            }
            String newLearning = new String(learningData, StandardCharsets.UTF_8);
            String newChat = new String(chatData, StandardCharsets.UTF_8);
            String newTasks = new String(taskData, StandardCharsets.UTF_8);
            LearningStore.validatePortable(newLearning);
            ChatStore.validatePortable(newChat);
            TaskStore.validatePortable(newTasks);
            // Pre-restore safety snapshots protect against a rare failure
            // in another SQLite file. Each individual import is transactional.
            try (FileOutputStream previous = new FileOutputStream(oldLibrary)) {
                documents.writePortableJson(previous);
                previous.getFD().sync();
            }
            oldLearning = learning.exportJson();
            oldChat = chat.exportJson();
            oldTasks = tasks.exportJson();
            changingDatabase = true;
            try (FileInputStream text = new FileInputStream(library)) {
                documents.restorePortableJson(text);
            }
            learning.importJson(newLearning);
            chat.importJson(newChat);
            tasks.importJson(newTasks);
            changingDatabase = false;
            return "✅ Личная рабочая среда восстановлена: документы, " +
                    "память, чат и журнал задач. Запуски GitHub НЕ " +
                    "повторяются автоматически. Ключи и GGUF необходимо " +
                    "настроить/импортировать отдельно.";
        } catch (Exception failure) {
            if (changingDatabase) {
                // Cross-database SQLite isn't globally atomic. Try restoring
                // previous snapshots without falsely claiming 100% guarantee.
                boolean rollbackOk = true;
                try (FileInputStream old = new FileInputStream(oldLibrary)) {
                    documents.restorePortableJson(old);
                } catch (Exception ignored) { rollbackOk = false; }
                try { learning.importJson(oldLearning); }
                catch (Exception ignored) { rollbackOk = false; }
                try { chat.importJson(oldChat); }
                catch (Exception ignored) { rollbackOk = false; }
                try { tasks.importJson(oldTasks); }
                catch (Exception ignored) { rollbackOk = false; }
                if (!rollbackOk)
                    throw new IllegalStateException("Восстановление прервано, " +
                            "а откат одной из баз не удался. НЕ удаляй исходный " +
                            "архив и сохрани текущие файлы приложения.", failure);
            }
            throw failure;
        } finally {
            if (passphrase != null) Arrays.fill(passphrase, '\0');
            ciphertext.delete();
            library.delete();
            oldLibrary.delete();
        }
    }
}
