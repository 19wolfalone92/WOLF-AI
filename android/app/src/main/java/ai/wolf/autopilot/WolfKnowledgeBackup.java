package ai.wolf.autopilot;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Portable across reinstalls: authenticated user-passphrase encrypted RAG
 * backups (AES-256-GCM + PBKDF2-HMAC-SHA256).
 *
 * Authentication is verified completely BEFORE any database mutation.
 * Backups contain indexed document plaintext; no chat, PAT, API token,
 * Metricool OAuth token or private signing keys are exported.
 */
public final class WolfKnowledgeBackup {
    private static final byte[] MAGIC = "WOLFKB8\n".getBytes(StandardCharsets.US_ASCII);
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int PBKDF2_ITERATIONS = 240_000;
    private static final int MAX_ARCHIVE = 55_000_000;
    private static final String ENTRY = "library.json";
    private WolfKnowledgeBackup() {}

    private static SecretKeySpec key(char[] password, byte[] salt) throws Exception {
        if (password == null || password.length < 12 || password.length > 256)
            throw new IllegalArgumentException("Пароль резервной копии: от 12 до 256 символов.");
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256);
        try {
            byte[] material = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(material, "AES");
            } finally { Arrays.fill(material, (byte) 0); }
        } finally {
            spec.clearPassword();
        }
    }

    private static void readExactly(InputStream in, byte[] bytes) throws IOException {
        int length = 0;
        while (length < bytes.length) {
            int count = in.read(bytes, length, bytes.length - length);
            if (count < 0)
                throw new IOException("Резервная копия обрезана.");
            length += count;
        }
    }

    public static String exportEncrypted(Context app, KnowledgeBase db,
                                         Uri target, char[] passphrase) throws Exception {
        if (target == null || !"content".equals(target.getScheme()))
            throw new IllegalArgumentException("Выбери файл через Android Files.");
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_BYTES], iv = new byte[IV_BYTES];
        random.nextBytes(salt);
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt),
                    new GCMParameterSpec(128, iv));
            cipher.updateAAD(MAGIC);
            int saved;
            try (OutputStream destination = app.getContentResolver().openOutputStream(target, "w")) {
                if (destination == null) throw new IOException("Нет доступа на запись.");
                destination.write(MAGIC);
                destination.write(salt);
                destination.write(iv);
                try (ZipOutputStream zip =
                             new ZipOutputStream(new CipherOutputStream(destination, cipher))) {
                    zip.setLevel(6);
                    zip.putNextEntry(new ZipEntry(ENTRY));
                    saved = db.writePortableJson(zip);
                    zip.closeEntry();
                    zip.finish();
                }
            }
            return "✅ Создана зашифрованная резервная копия: " + saved +
                    " документов. Сохрани пароль ОТДЕЛЬНО: без него " +
                    "восстановить файл невозможно. Другие данные приложения " +
                    "(чат, задачи и токены) в архив НЕ входят.";
        } finally {
            if (passphrase != null) Arrays.fill(passphrase, '\0');
        }
    }

    public static String restoreEncrypted(Context app, KnowledgeBase db,
                                          Uri source, char[] passphrase) throws Exception {
        if (source == null || !"content".equals(source.getScheme()))
            throw new IllegalArgumentException("Выбери зашифрованную копию через Android Files.");
        File stagingZip = File.createTempFile("wolf-kb-cipher-", ".zip", app.getCacheDir());
        File stagingJson = File.createTempFile("wolf-kb-payload-", ".json", app.getCacheDir());
        try {
            try (InputStream raw = app.getContentResolver().openInputStream(source)) {
                if (raw == null) throw new IOException("Нет доступа к резервной копии.");
                byte[] magic = new byte[MAGIC.length];
                readExactly(raw, magic);
                if (!Arrays.equals(MAGIC, magic))
                    throw new IllegalArgumentException("Это не зашифрованная копия WOLF 8.");
                byte[] salt = new byte[SALT_BYTES], iv = new byte[IV_BYTES];
                readExactly(raw, salt);
                readExactly(raw, iv);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt),
                        new GCMParameterSpec(128, iv));
                cipher.updateAAD(MAGIC);
                // Stage and FULLY authenticate ciphertext BEFORE touching
                // any SQLite row. A wrong password must not erase old data.
                try (CipherInputStream decrypted = new CipherInputStream(raw, cipher);
                     FileOutputStream dest = new FileOutputStream(stagingZip)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = decrypted.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_ARCHIVE)
                            throw new IllegalArgumentException("Архив превышает 55 МБ.");
                        dest.write(buffer, 0, n);
                    }
                    dest.getFD().sync();
                } catch (IOException error) {
                    throw new IllegalArgumentException(
                            "Ошибка проверки AES-GCM: неверный пароль или повреждённый файл.");
                }
            }
            // Then validate the ZIP content/CRC before restoring the
            // database (each stream entry/size/entry count bounded).
            try (ZipInputStream zip = new ZipInputStream(new FileInputStream(stagingZip));
                 FileOutputStream json = new FileOutputStream(stagingJson)) {
                ZipEntry first = zip.getNextEntry();
                if (first == null || !ENTRY.equals(first.getName()) || first.isDirectory())
                    throw new IllegalArgumentException("Некорректная структура архива.");
                byte[] buffer = new byte[8192];
                long length = 0;
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    length += n;
                    if (length > MAX_ARCHIVE)
                        throw new IllegalArgumentException("JSON превышает лимит 55 МБ.");
                    json.write(buffer, 0, n);
                }
                zip.closeEntry(); // ZIP CRC check
                if (zip.getNextEntry() != null)
                    throw new IllegalArgumentException("В архиве неожиданные дополнительные файлы.");
                json.getFD().sync();
            }
            int restored;
            try (FileInputStream plain = new FileInputStream(stagingJson)) {
                restored = db.restorePortableJson(plain);
            }
            return "✅ Восстановлена зашифрованная библиотека: " + restored +
                    " документов. Старые документы были заменены. " +
                    "Чат, задания и токены не изменены.";
        } finally {
            if (passphrase != null) Arrays.fill(passphrase, '\0');
            stagingZip.delete();
            stagingJson.delete();
        }
    }
}
