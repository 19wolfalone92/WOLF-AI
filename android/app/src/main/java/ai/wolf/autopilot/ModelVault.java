package ai.wolf.autopilot;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.StatFs;
import android.provider.OpenableColumns;
import android.database.Cursor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Offline imported GGUF models. Only SAF-selected data is copied, never
 * arbitrary paths or URLs. Model blobs remain app-private; at most ONE
 * custom model is active. A failure never replaces the previous working model.
 */
public final class ModelVault {
    private static final String PREF = "wolf_custom_model_v1";
    private static final String MODEL = "active.gguf";
    private static final long MIN_GGUF_BYTES = 100_000_000L;
    private static final long MAX_GGUF_BYTES = 3_600_000_000L;
    private static final byte[] MAGIC = { 0x47, 0x47, 0x55, 0x46 }; // GGUF
    private static final Object LOCK = new Object();

    private ModelVault() {}

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static File folder(Context c) {
        return new File(c.getFilesDir(), "models");
    }

    private static File target(Context c) {
        return new File(folder(c), MODEL);
    }

    public static String name(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean("enabled", false) ?
                "Импортированная: " + p.getString("name", "GGUF") : "Qwen2.5-Coder 0.5B (встроенная)";
    }

    public static String summary(Context context) {
        SharedPreferences p = prefs(context);
        File f = target(context);
        if (!p.getBoolean("enabled", false) || !f.isFile()) {
            String reason=p.getString("suspended_reason","");
            return "⚡ Быстрая модель 0.5B в APK. Можно импортировать GGUF до 3.6 ГБ."+
                    (reason.isEmpty()?"":"\n⚠️ Предыдущая модель не запустилась: "+reason);
        }
        return "🧠 " + name(context) + " • " +
                String.format(Locale.ROOT, "%.2f ГБ", f.length() / 1e9) +
                "\nSHA-256: " + p.getString("sha256", "not available") +
                "\nХранится только на устройстве, без платного API.";
    }

    /** Return installed external model or null to use the bundled 0.5B. */
    public static File active(Context context) {
        // A lock here would invert EmbeddedAi.LOCK -> ModelVault.LOCK during
        // import/unload and could deadlock concurrent model operations.
        SharedPreferences p = prefs(context);
        File file = target(context);
        if (!p.getBoolean("enabled", false))
            return null;
        if (!file.isFile() || file.length() != p.getLong("size", -1) ||
                !magicOk(file))
            throw new IllegalStateException("Импортированный GGUF повреждён или удалён. " +
                    "Удалите модель в настройках и выберите другую.");
        return file;
    }

    public static String selectedName(Context context, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme()))
            throw new IllegalArgumentException("Импортировать модель нужно через файлы Android.");
        String name = "model.gguf";
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && !cursor.isNull(idx)) name = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        if (name == null || name.length() > 160 ||
                !name.toLowerCase(Locale.ROOT).endsWith(".gguf"))
            throw new IllegalArgumentException("Нужен одиночный файл .gguf, не ZIP и не safetensors.");
        return name;
    }

    private static boolean magicOk(File f) {
        try (FileInputStream in = new FileInputStream(f)) {
            for (byte expected : MAGIC) {
                if (in.read() != (expected & 255)) return false;
            }
            int version = in.read() | (in.read() << 8) | (in.read() << 16) | (in.read() << 24);
            return version == 2 || version == 3;
        } catch (Exception ignored) { return false; }
    }

    private static String digestHex(byte[] bytes) {
        StringBuilder b = new StringBuilder();
        for (byte value : bytes) b.append(String.format(Locale.ROOT, "%02x", value & 255));
        return b.toString();
    }

    public static String importFile(Context context, Uri uri) throws Exception {
        Context app = context.getApplicationContext();
        String displayName = selectedName(app, uri);
        synchronized (LOCK) {
            File dir = folder(app);
            if (!dir.isDirectory() && !dir.mkdirs())
                throw new IllegalStateException("Невозможно создать каталог локальных моделей.");
            // Includes space for the incoming GGUF, plus a safety reserve.
            long free = new StatFs(dir.getAbsolutePath()).getAvailableBytes();
            if (free < 900_000_000L)
                throw new IllegalStateException("Для импорта необходимо не менее 900 МБ свободного места.");
            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
                am.getMemoryInfo(memory);
                if (memory.totalMem < 4_000_000_000L)
                    throw new IllegalStateException("На устройстве недостаточно оперативной памяти для больших моделей.");
            }

            File partial = new File(dir, "incoming.gguf.partial");
            File oldBackup = new File(dir, "previous.gguf.backup");
            if (partial.exists() && !partial.delete())
                throw new IllegalStateException("Не удалось очистить предыдущий неполный импорт.");
            if (oldBackup.exists() && !oldBackup.delete())
                throw new IllegalStateException("Не удалось очистить предыдущую резервную копию.");
            long count = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = app.getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(partial)) {
                if (in == null) throw new IllegalArgumentException("Невозможно прочитать выбранный GGUF.");
                byte[] chunk = new byte[1024 * 1024];
                int length;
                while ((length = in.read(chunk)) >= 0) {
                    count += length;
                    if (count > MAX_GGUF_BYTES || count + 250_000_000L > free)
                        throw new IllegalStateException("Файл слишком большой или на диске мало места. " +
                                "Лимит: 3.6 ГБ.");
                    digest.update(chunk, 0, length);
                    out.write(chunk, 0, length);
                }
                out.getFD().sync();
            } catch (Exception e) {
                partial.delete();
                throw e;
            }
            if (count < MIN_GGUF_BYTES || !magicOk(partial)) {
                partial.delete();
                throw new IllegalArgumentException("Это не поддерживаемый одиночный GGUF (v2/v3).");
            }
            String sha = digestHex(digest.digest());
            // Native code must no longer hold an mmap to an old model file.
            EmbeddedAi.unload();
            File existing = target(app);
            boolean backup = existing.exists();
            if (backup && !existing.renameTo(oldBackup)) {
                partial.delete();
                throw new IllegalStateException("Не удалось сохранить предыдущую рабочую модель.");
            }
            if (!partial.renameTo(existing)) {
                if (backup) oldBackup.renameTo(existing);
                partial.delete();
                throw new IllegalStateException("Не удалось установить GGUF. Старая модель восстановлена.");
            }
            prefs(app).edit().putBoolean("enabled", true)
                    .putString("name", displayName)
                    .putString("sha256", sha)
                    .putLong("size", count)
                    .apply();
            if (backup) oldBackup.delete();
            return "✅ Установлена локальная модель: " + displayName + "\n" +
                    String.format(Locale.ROOT, "Размер: %.2f ГБ", count / 1e9) +
                    "\nSHA-256: " + sha +
                    "\nПроверь запуск модели: поддержка зависит от llama.cpp, " +
                    "RAM и архитектуры GGUF.";
        }
    }

    /**
     * Install only a trusted, SHA256-pinned download from our private external
     * download folder. Stream in chunks; never map a 2.5 GB file in the JVM.
     * The active file and prefs remain unchanged until AFTER verification.
     */
    public static String installVerifiedDownload(Context context, File source,
                                                  OfflineModelCatalog.Entry entry) throws Exception {
        if (!OfflineModelCatalog.trusted(entry))
            throw new IllegalArgumentException("Модель отсутствует в доверенном каталоге.");
        Context app=context.getApplicationContext();
        File external=app.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        if(external==null)
            throw new IllegalStateException("Недоступна папка загрузок приложения.");
        File allowed=new File(external,entry.downloadName);
        if(source==null || !source.isFile() ||
                !source.getCanonicalPath().equals(allowed.getCanonicalPath()))
            throw new IllegalArgumentException("Файл не из защищённой загрузки WOLF.");
        if(source.length()<entry.minBytes || source.length()>entry.maxBytes)
            throw new IllegalArgumentException("Неверный размер загруженной модели.");
        synchronized(LOCK) {
            File dir=folder(app);
            if(!dir.isDirectory() && !dir.mkdirs())
                throw new IllegalStateException("Не удалось создать каталог моделей.");
            long free=new StatFs(dir.getAbsolutePath()).getAvailableBytes();
            if(free<source.length()+300_000_000L)
                throw new IllegalStateException(
                        "Не хватает свободного места для проверки и установки GGUF. " +
                        "Нужно размер модели + 300 МБ.");
            File partial=new File(dir,"downloaded-verified.gguf.partial");
            File backup=new File(dir,"previous.gguf.backup");
            if(partial.exists()&&!partial.delete())
                throw new IllegalStateException("Не удалось очистить неполный импорт.");
            if(backup.exists()&&!backup.delete())
                throw new IllegalStateException("Не удалось очистить старую резервную копию.");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            long count=0;
            try(InputStream in=new FileInputStream(source);
                FileOutputStream out=new FileOutputStream(partial)) {
                byte[] bytes=new byte[1024*1024];
                int n;
                while((n=in.read(bytes))!=-1) {
                    count+=n;
                    if(count>entry.maxBytes)
                        throw new IllegalStateException("Входной файл превышает лимит.");
                    out.write(bytes,0,n);
                    digest.update(bytes,0,n);
                }
                out.getFD().sync();
            } catch(Exception error) {
                partial.delete();
                throw error;
            }
            String sha=digestHex(digest.digest());
            if(count<entry.minBytes || count>entry.maxBytes ||
                    !sha.equals(entry.sha256) || !magicOk(partial)) {
                partial.delete();
                throw new IllegalStateException(
                        "SHA-256 / формат модели не совпадает с проверенным каталогом. " +
                        "Предыдущая модель не изменена.");
            }
            EmbeddedAi.unload();
            File active=target(app);
            boolean hadOld=active.isFile();
            if(hadOld && !active.renameTo(backup)) {
                partial.delete();
                throw new IllegalStateException("Не удалось сохранить прежнюю модель.");
            }
            if(!partial.renameTo(active)) {
                if(hadOld)backup.renameTo(active);
                partial.delete();
                throw new IllegalStateException(
                        "Не удалось применить новую модель. Старая восстановлена.");
            }
            boolean persisted=prefs(app).edit()
                    .putBoolean("enabled",true)
                    .putString("name",entry.title+".gguf")
                    .putString("sha256",sha)
                    .putLong("size",count)
                    .commit();
            if(!persisted) {
                active.delete();
                if(hadOld)backup.renameTo(active);
                throw new IllegalStateException(
                        "Не удалось сохранить выбор модели. Восстановлена предыдущая.");
            }
            if(hadOld)backup.delete();
            source.delete(); // Download manager target is app-private and verified.
            return "✅ "+entry.title+" установлена локально. SHA256: "+sha+
                    "\nГотово к проверке запуска. " +
                    "Модель работает без облачного API, но скорость и RAM зависят от телефона.";
        }
    }

    /**
     * A recently downloaded GGUF may be too new for this pinned native engine.
     * Fail safe: stop using it, retain the file for diagnosis/reimport, and
     * allow the bundled 0.5B to work instead of breaking ordinary chat.
     */
    public static void suspendUnsupported(Context context, String detail) {
        String reason=(detail==null?"Неизвестная ошибка загрузки":detail);
        if(reason.length()>180)reason=reason.substring(0,180);
        if(!prefs(context).edit()
                .putBoolean("enabled",false)
                .putString("suspended_reason",reason)
                .commit())
            throw new IllegalStateException("Не удалось переключиться на резервную модель.");
    }

    public static String reset(Context context) {
        synchronized (LOCK) {
            EmbeddedAi.unload();
            prefs(context).edit().clear().apply();
            File target = target(context);
            if (target.isFile() && !target.delete())
                return "Переключено на встроенную Qwen 0.5B, но импортированный " +
                        "файл пока не удалился. Попробуйте повторить.";
            return "✅ Импортированная модель удалена. Активна встроенная Qwen 0.5B.";
        }
    }
}
