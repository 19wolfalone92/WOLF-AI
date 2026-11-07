package ai.wolf.autopilot;

import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Free, auditable Wikimedia encyclopaedia search. NOT arbitrary browsing and
 * NOT a paid search engine or an LLM with real-time awareness.
 *
 * Queries leave the device ONLY after the user taps Search. Never sends
 * chat, local notes, GitHub PAT, Metricool OAuth or private project files.
 */
public final class WikipediaResearch {
    private WikipediaResearch() {}

    public static final class Result {
        public final String display;
        public final String firstNote;
        public final String sourceLabel;

        Result(String display, String firstNote, String sourceLabel) {
            this.display = display; this.firstNote = firstNote;
            this.sourceLabel = sourceLabel;
        }
    }

    public static Result search(String query, String language) throws Exception {
        if (query == null || query.trim().length() < 3 || query.length() > 180)
            throw new IllegalArgumentException("Запрос: от 3 до 180 символов.");
        if (!("ru".equals(language) || "en".equals(language)))
            throw new IllegalArgumentException("Поддерживаются только русская и английская Википедия.");
        String target = "https://" + language + ".wikipedia.org/w/api.php" +
                "?action=query&list=search&format=json&formatversion=2" +
                "&srnamespace=0&srlimit=5&srprop=snippet%7Ctimestamp" +
                "&srsearch=" + URLEncoder.encode(query.trim(), "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(target).openConnection();
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(18000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent",
                    "WolfAI/4.0 (open source; contact via github.com/19wolfalone92/WOLF-AI)");
            if (c.getResponseCode() != 200)
                throw new IllegalStateException("Wikimedia API ответил HTTP " +
                        c.getResponseCode() + ". Попробуй позже.");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 180000)
                        throw new IllegalStateException("Слишком большой ответ энциклопедии.");
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject root = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            JSONArray results = root.getJSONObject("query").getJSONArray("search");
            StringBuilder summary = new StringBuilder(
                    "📖 Результаты Wikimedia (" + language +
                    "). Энциклопедия — НЕ все источники интернета.\n\n");
            String first = null;
            int added = 0;
            for (int i = 0; i < results.length() && added < 5; i++) {
                JSONObject row = results.getJSONObject(i);
                int page = row.optInt("pageid", 0);
                if (page < 1) continue;
                String title = row.optString("title", "Без названия");
                if (title.length() > 150) title = title.substring(0, 150);
                String snippet = Html.fromHtml(row.optString("snippet", ""),
                        Html.FROM_HTML_MODE_LEGACY).toString()
                        .replace('\n', ' ').trim();
                if (snippet.length() > 260) snippet = snippet.substring(0, 260) + "…";
                String source = "https://" + language + ".wikipedia.org/?curid=" + page;
                summary.append(added + 1).append(". ").append(title).append("\n")
                        .append(snippet).append("\n").append(source).append("\n\n");
                if (first == null) {
                    String note = "Источник: " + title + ". " + snippet + " (" + source + ")";
                    first = note.substring(0, Math.min(note.length(), 590));
                }
                added++;
            }
            if (added == 0)
                return new Result("Ничего не найдено в Википедии. Попробуй уточнить запрос.",
                        null, "Wikipedia");
            summary.append("⚠️ Для технических решений сверяй первичные документы и даты. " +
                    "Сохранение найденного в память выполняется отдельно, только по кнопке.");
            return new Result(summary.toString(), first, "Wikipedia");
        } finally { c.disconnect(); }
    }
}
