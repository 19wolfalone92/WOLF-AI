package ai.wolf.autopilot;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.content.ContentResolver;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.util.Locale;

/**
 * Official Android video handoff. The user chooses a document and explicitly
 * taps share in TikTok, YouTube, Instagram or another installed app.
 * NO automatic posting or third-party API authorization is implied.
 */
public final class PublicationCenter {
    public static final int PICK_VIDEO = 6130;
    public static final String TIKTOK = "TikTok";
    public static final String INSTAGRAM = "Instagram";
    public static final String YOUTUBE = "YouTube";
    public static final String FACEBOOK = "Facebook";
    public static final String OTHER = "Другое приложение";
    public static final String[] NETWORKS = {TIKTOK, INSTAGRAM, YOUTUBE, FACEBOOK, OTHER};

    public static final class VideoInfo {
        public final Uri uri;
        public final String name;
        public final long bytes;
        public final int width, height;
        public final long durationMs;

        VideoInfo(Uri uri, String name, long bytes, int width, int height, long durationMs) {
            this.uri = uri; this.name = name; this.bytes = bytes;
            this.width = width; this.height = height; this.durationMs = durationMs;
        }

        public String description() {
            String size = bytes < 0 ? "размер неизвестен" :
                    String.format(Locale.ROOT, "%.1f МБ", bytes / (1024d * 1024d));
            String portrait = width > 0 && height > 0 ?
                    (height >= width ? "вертикальное" : "горизонтальное") : "ориентация неизвестна";
            String seconds = durationMs > 0 ? (durationMs / 1000) + " с" : "время неизвестно";
            return name + "\n" + width + " × " + height +
                    " • " + portrait + " • " + seconds + " • " + size;
        }

        public String warning() {
            if (width <= 0 || height <= 0 || durationMs <= 0)
                return "Нет достоверных метаданных — перед публикацией проверь файл вручную.";
            if (height < width) return "Горизонтальное видео — для Shorts/Reels/TikTok обычно лучше 9:16.";
            if (width < 720 || height < 1280) return "Невысокое разрешение. Рекомендуется 1080×1920.";
            if (durationMs > 180000) return "Длинное видео. Проверь ограничения конкретной платформы.";
            return "Базовые параметры видео подходят для вертикального короткого ролика.";
        }
    }

    private PublicationCenter() {}

    public static Intent pickIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/mp4"); // select actual MP4, not an arbitrary URL
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        return intent;
    }

    public static VideoInfo inspect(Activity activity, Uri uri) throws Exception {
        if (uri == null || !"content".equals(uri.getScheme()))
            throw new IllegalArgumentException("Выбери видео через системный файловый менеджер.");
        ContentResolver resolver = activity.getContentResolver();
        String mime = resolver.getType(uri);
        if (mime != null && !mime.equals("video/mp4") && !mime.equals("application/mp4"))
            throw new IllegalArgumentException("Нужно видео MP4. Обнаружено: " + mime);
        String name = "video.mp4";
        long size = -1;
        try (Cursor c = resolver.query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int nameColumn = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = c.getColumnIndex(OpenableColumns.SIZE);
                if (nameColumn >= 0) name = c.getString(nameColumn);
                if (sizeColumn >= 0 && !c.isNull(sizeColumn)) size = c.getLong(sizeColumn);
            }
        }
        if (size > 1_500_000_000L) throw new IllegalArgumentException("Видео больше 1.5 ГБ.");
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".mp4"))
            throw new IllegalArgumentException("Выбранный файл должен иметь расширение .mp4.");
        int width = 0, height = 0;
        long duration = -1;
        MediaMetadataRetriever reader = new MediaMetadataRetriever();
        try {
            reader.setDataSource(activity, uri);
            width = parsePositive(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            height = parsePositive(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            duration = parsePositive(reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } finally { reader.release(); }
        if (duration <= 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Невозможно проверить параметры MP4.");
        if (duration > 45 * 60 * 1000L)
            throw new IllegalArgumentException("Слишком длинное видео для этого интерфейса.");
        return new VideoInfo(uri, name, size, width, height, duration);
    }

    private static int parsePositive(String value) {
        if (value == null) return 0;
        try { return Math.max(0, Integer.parseInt(value)); }
        catch (NumberFormatException e) {return 0;}
    }

    public static Intent shareIntent(VideoInfo info, String title) {
        if (info == null) throw new IllegalArgumentException("Сначала выбери MP4.");
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("video/mp4");
        intent.putExtra(Intent.EXTRA_STREAM, info.uri);
        intent.setClipData(ClipData.newRawUri("WOLF video", info.uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (title != null && !title.trim().isEmpty()) {
            intent.putExtra(Intent.EXTRA_TEXT, title.trim());
        }
        return intent;
    }

    private static String[] packages(String platform) {
        if (TIKTOK.equals(platform))
            return new String[]{"com.zhiliaoapp.musically", "com.ss.android.ugc.trill"};
        if (INSTAGRAM.equals(platform)) return new String[]{"com.instagram.android"};
        if (YOUTUBE.equals(platform)) return new String[]{"com.google.android.youtube"};
        if (FACEBOOK.equals(platform)) return new String[]{"com.facebook.katana"};
        return new String[0];
    }

    public static void share(Activity activity, VideoInfo info,
                             String platform, String title) {
        // This just opens the receiving app: the actual publish button stays
        // entirely under user control, including visibility, music and captions.
        for (String pkg : packages(platform)) {
            Intent candidate = shareIntent(info, title);
            candidate.setPackage(pkg);
            try {
                activity.startActivity(candidate);
                return;
            } catch (ActivityNotFoundException ignored) {
                // Fall through to regular Android sharesheet, no auto-upload.
            }
        }
        activity.startActivity(Intent.createChooser(
                shareIntent(info, title), "Открыть видео для публикации"));
    }
}
