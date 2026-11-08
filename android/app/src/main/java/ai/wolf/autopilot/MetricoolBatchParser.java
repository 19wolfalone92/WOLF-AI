package ai.wolf.autopilot;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure Java, testable batch planner. Input:
 * ISO_OFFSET_DATE | PUBLIC_MP4_URL | CAPTION
 *
 * No publication is attempted by parsing; external writes require an
 * explicit preview and one user confirmation of the ENTIRE exact batch.
 */
public final class MetricoolBatchParser {
    private MetricoolBatchParser() {}

    public static final class Item {
        public final String dateIso;
        public final String url;
        public final String caption;
        Item(String dateIso, String url, String caption) {
            this.dateIso = dateIso;
            this.url = url;
            this.caption = caption;
        }
    }

    public static List<Item> parse(String text, String timezone, Instant now) {
        if (text == null || text.length() > 20000)
            throw new IllegalArgumentException("Расписание слишком длинное.");
        if (!("Europe/Minsk".equals(timezone) ||
                "Europe/Amsterdam".equals(timezone) || "UTC".equals(timezone)))
            throw new IllegalArgumentException("Неподдерживаемый часовой пояс.");
        String[] lines = text.replace("\r", "").split("\n", -1);
        List<Item> items = new ArrayList<>();
        Set<String> dates = new HashSet<>();
        Set<String> media = new HashSet<>();
        for (String row : lines) {
            if (row.trim().isEmpty()) continue;
            if (items.size() >= 12)
                throw new IllegalArgumentException("Не более 12 публикаций за одну операцию.");
            String[] values = row.split("\\|", 3);
            if (values.length != 3)
                throw new IllegalArgumentException("Строка " + (items.size() + 1) +
                        ": формат ДАТА | URL | ПОДПИСЬ.");
            String date = values[0].trim();
            String url = values[1].trim();
            String caption = values[2].trim();
            try {
                OffsetDateTime when = OffsetDateTime.parse(date);
                if (!when.toInstant().isAfter(now.plusSeconds(300)) ||
                        when.toInstant().isAfter(now.plusSeconds(365L * 24 * 3600)))
                    throw new IllegalArgumentException("Дата должна быть от 5 минут до года в будущем.");
                if (!when.getOffset().equals(ZoneId.of(timezone)
                        .getRules().getOffset(when.toInstant())))
                    throw new IllegalArgumentException("Часовой пояс и UTC-смещение не совпадают.");
                if (!dates.add(when.toInstant().toString()))
                    throw new IllegalArgumentException("Два поста назначены на одно и то же время.");
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Строка " + (items.size()+1) +
                        ": неверная дата: " + error.getMessage());
            }
            if (url.length() < 15 || url.length() > 1500 || url.contains("@"))
                throw new IllegalArgumentException("Строка " + (items.size()+1) +
                        ": нужен публичный HTTPS URL файла MP4.");
            try {
                URI uri = new URI(url);
                if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                        uri.getHost() == null || uri.getRawUserInfo() != null ||
                        uri.getPort() != -1 || uri.getFragment() != null ||
                        !uri.getPath().toLowerCase(java.util.Locale.ROOT).endsWith(".mp4") ||
                        uri.getHost().equalsIgnoreCase("localhost") ||
                        uri.getHost().matches("(?i)(?:127\\..*|10\\..*|192\\.168\\..*|172\\.(?:1[6-9]|2[0-9]|3[01])\\..*|.*\\.local)"))
                    throw new IllegalArgumentException("Используй обычный публичный HTTPS MP4.");
            } catch (Exception error) {
                throw new IllegalArgumentException("Строка " + (items.size()+1) +
                        ": недопустимый URL: " + error.getMessage());
            }
            if (!media.add(url))
                throw new IllegalArgumentException("Один MP4 повторяется в расписании.");
            if (caption.isEmpty() || caption.length() > 1800)
                throw new IllegalArgumentException("Строка " + (items.size()+1) +
                        ": подпись должна содержать 1–1800 символов.");
            items.add(new Item(date, url, caption));
        }
        if (items.isEmpty())
            throw new IllegalArgumentException("Добавь хотя бы одну строку расписания.");
        return items;
    }

    public static String preview(List<Item> items, String network,
                                 String timezone, boolean autoPublish) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("Пустой список.");
        StringBuilder out = new StringBuilder("СЕТЬ: ").append(network)
                .append("\nПОЯС: ").append(timezone)
                .append("\nРЕЖИМ: ").append(autoPublish ? "АВТОПУБЛИКАЦИЯ" : "ЧЕРНОВИК")
                .append("\nПОСТОВ: ").append(items.size()).append("\n\n");
        for (int i=0; i<items.size(); i++) {
            Item item = items.get(i);
            out.append(i+1).append(") ").append(item.dateIso)
                    .append("\n").append(item.url).append("\n")
                    .append(item.caption).append("\n\n");
        }
        return out.toString();
    }
}
