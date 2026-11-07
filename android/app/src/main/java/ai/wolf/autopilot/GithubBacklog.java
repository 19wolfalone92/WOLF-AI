package ai.wolf.autopilot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Deliberately limited GitHub issue backlog. Never starts code execution and
 * never makes source changes. Issues in WOLF-AI are PUBLIC.
 */
public final class GithubBacklog {
    private static final String API =
            "https://api.github.com/repos/19wolfalone92/WOLF-AI";
    public static final class IssueReceipt {
        public final long number;
        public final String message;
        IssueReceipt(long number) {
            this.number = number;
            this.message = "Задача опубликована в GitHub Issues #" + number +
                    ".\nhttps://github.com/19wolfalone92/WOLF-AI/issues/" + number +
                    "\nОна поставлена в список, но НЕ выполнена.";
        }
    }

    private GithubBacklog() {}

    private static HttpURLConnection connect(String suffix, String method, String token) throws Exception {
        if (!("/issues".equals(suffix) || "/issues?state=open&per_page=12".equals(suffix) ||
                "/pulls?state=open&per_page=12".equals(suffix)))
            throw new IllegalArgumentException("Недопустимый путь GitHub.");
        HttpURLConnection c = (HttpURLConnection) new URL(API + suffix).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(12000);
        c.setReadTimeout(16000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "WolfAI-Android/2.1");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }

    private static String body(HttpURLConnection c) throws Exception {
        InputStream input = c.getResponseCode() >= 400 ? c.getErrorStream() : c.getInputStream();
        if (input == null) return "";
        try (InputStream in = input; ByteArrayOutputStream data = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (data.size() + n > 180000) throw new IllegalStateException("Слишком большой ответ GitHub.");
                data.write(buffer, 0, n);
            }
            return data.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** Detect common accidental secrets; no task text is submitted automatically. */
    public static boolean suspectedSecret(String text) {
        if (text == null) return false;
        return text.matches("(?is).*(?:github_pat_|ghp_[A-Za-z0-9]|sk-[A-Za-z0-9]{12}|AKIA[A-Z0-9]{12}|password\\s*[:=]|bearer\\s+[A-Za-z0-9]).*");
    }

    public static IssueReceipt create(String text, String project, String mode,
                                String uid, String token) throws Exception {
        if (token == null || token.isEmpty())
            throw new IllegalStateException("Нужен GitHub PAT с разрешением Issues: Read and write на WOLF-AI.");
        if (text == null || text.trim().length() < 5 || text.length() > 600 ||
                !CommandParser.validProject(project) ||
                !("audit".equals(mode) || "test".equals(mode)) ||
                uid == null || !uid.matches("w[a-f0-9]{32}"))
            throw new IllegalArgumentException("Некорректный текст или параметры задания.");
        if (suspectedSecret(text))
            throw new IllegalArgumentException("В задании обнаружен возможный секрет. Удали его.");

        String headline = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (headline.length() > 90) headline = headline.substring(0, 90) + "…";
        JSONObject payload = new JSONObject();
        payload.put("title", "[WOLF TASK] " + project + ": " + headline);
        payload.put("body", "Задача, отправленная пользователем из WOLF AI Android.\n\n" +
                "**Проект:** " + project + "\n" +
                "**Режим:** " + mode + "\n" +
                "**ID задания:** " + uid + "\n\n" +
                "### Описание\n\n" + text.trim() + "\n\n" +
                "> ВАЖНО: это публичная запись задачи, а НЕ подтверждение выполнения. " +
                "Никаких автоматических коммитов, торговли или публикаций.");
        HttpURLConnection c = connect("/issues", "POST", token);
        try {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            int code = c.getResponseCode();
            String response = body(c);
            if (code != 201) throw new IllegalStateException(
                    "Issue не создан: GitHub HTTP " + code + ". Проверь права Issues.");
            JSONObject issue = new JSONObject(response);
            long number = issue.getLong("number");
            return new IssueReceipt(number);
        } finally {
            c.disconnect();
        }
    }

    public static String list(boolean pulls, String token) throws Exception {
        HttpURLConnection c = connect(
                pulls ? "/pulls?state=open&per_page=12" : "/issues?state=open&per_page=12",
                "GET", token);
        try {
            int code = c.getResponseCode();
            String response = body(c);
            if (code != 200)
                throw new IllegalStateException("GitHub ответил HTTP " + code + ".");
            JSONArray items = new JSONArray(response);
            StringBuilder summary = new StringBuilder();
            summary.append(pulls ? "Открытые Pull Requests:" : "Открытые задания Issues:").append("\n");
            int shown = 0;
            for (int i = 0; i < items.length() && shown < 12; i++) {
                JSONObject item = items.getJSONObject(i);
                if (!pulls && item.has("pull_request")) continue;
                int number = item.getInt("number");
                String title = item.optString("title", "Без названия")
                        .replace('\n', ' ').replace('\r', ' ');
                if (title.length() > 100) title = title.substring(0, 100) + "…";
                summary.append("#").append(number).append(": ").append(title).append("\n")
                       .append("https://github.com/19wolfalone92/WOLF-AI/")
                       .append(pulls ? "pull/" : "issues/").append(number).append("\n");
                shown++;
            }
            if (shown == 0) summary.append("Ничего нет.\n");
            return summary.toString();
        } finally {
            c.disconnect();
        }
    }
}
