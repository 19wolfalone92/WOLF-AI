package ai.wolf.autopilot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * WOLF-approved Issue mission controls. Every approval is an explicit signed
 * SHA-256 request in the user's OWN GitHub Issue, never an LLM command.
 *
 * The job itself is limited to the existing GitHub audit/test workflow,
 * and is reconciled by a finite scheduled worker.
 */
public final class GithubMissions {
    private static final String ROOT =
            "https://api.github.com/repos/19wolfalone92/WOLF-AI";
    private static final int MAX_RESPONSE = 190000;
    private GithubMissions() {}

    private static void validIssue(long number) {
        if (number < 1 || number > 1_000_000_000L)
            throw new IllegalArgumentException("Неверный номер GitHub Issue.");
    }

    private static HttpURLConnection connection(String suffix, String method, String token)
            throws Exception {
        if (token == null || token.isEmpty())
            throw new IllegalStateException("GitHub PAT: нужен Issues Read and write на WOLF-AI.");
        if (!(method.equals("GET") || method.equals("POST")) ||
                !suffix.startsWith("/issues/") || suffix.contains("..") ||
                suffix.contains("//") || suffix.contains("%") ||
                suffix.contains("://"))
            throw new IllegalArgumentException("Неверная операция GitHub Issues.");
        HttpURLConnection c = (HttpURLConnection) new URL(ROOT + suffix).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(16000);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "WolfAI-Android/4.0");
        return c;
    }

    private static String body(HttpURLConnection c) throws Exception {
        int status = c.getResponseCode();
        if (status < 200 || status >= 300)
            throw new IllegalStateException("GitHub HTTP " + status +
                    ". Проверь права Issues, наличие задачи и подключение.");
        try (InputStream in = c.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (in == null) throw new IllegalStateException("GitHub вернул пустой ответ.");
            byte[] buffer = new byte[4096];
            int size;
            while ((size = in.read(buffer)) != -1) {
                if (out.size() + size > MAX_RESPONSE)
                    throw new IllegalStateException("Слишком большой ответ GitHub.");
                out.write(buffer, 0, size);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : digest) out.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return out.toString();
    }

    private static final class Mission {
        final long issue;
        final String uid, project, mode, fingerprint;

        Mission(long issue, String uid, String project, String mode, String fingerprint) {
            this.issue=issue; this.uid=uid; this.project=project;
            this.mode=mode; this.fingerprint=fingerprint;
        }

        String approval() {
            return "/wolf run " + uid + " " + fingerprint;
        }

        String marker() {
            return "WOLF-MISSION/v1 issue=" + issue + " uid=" + uid +
                    " sha=" + fingerprint + " ";
        }
    }

    private static Mission fetch(long number, String uid, String token) throws Exception {
        validIssue(number);
        if (uid == null || !uid.matches("w[a-f0-9]{32}"))
            throw new IllegalArgumentException("Неверный идентификатор задачи.");
        HttpURLConnection c = connection("/issues/" + number, "GET", token);
        JSONObject issue;
        try { issue = new JSONObject(body(c)); }
        finally { c.disconnect(); }
        if (!issue.optString("title", "").startsWith("[WOLF TASK] ") ||
                !"open".equals(issue.optString("state")) ||
                !"19wolfalone92".equals(issue.getJSONObject("user").optString("login")) ||
                issue.has("pull_request"))
            throw new IllegalStateException("Задача должна быть открытым Issue владельца WOLF-AI.");
        String content = issue.getString("body");
        // Bind to the full public issue body, including exact mode and project.
        if (!content.contains("**ID задания:** " + uid + "\n"))
            throw new IllegalArgumentException("Issue не связан с этой локальной задачей.");
        String project = null, mode = null;
        for (String line : content.split("\n")) {
            if (line.startsWith("**Проект:** ")) {
                if (project != null) throw new IllegalArgumentException("Повтор проекта в Issue.");
                project = line.substring("**Проект:** ".length()).trim();
            }
            if (line.startsWith("**Режим:** ")) {
                if (mode != null) throw new IllegalArgumentException("Повтор режима в Issue.");
                mode = line.substring("**Режим:** ".length()).trim();
            }
        }
        if (!CommandParser.validProject(project) ||
                !("audit".equals(mode) || "test".equals(mode)) ||
                !issue.getString("title").startsWith("[WOLF TASK] " + project + ":"))
            throw new IllegalArgumentException("Некорректный проект или режим Issue.");
        return new Mission(number, uid, project, mode, sha256(content));
    }

    private static JSONArray comments(long number, String token) throws Exception {
        HttpURLConnection c = connection("/issues/" + number + "/comments?per_page=100",
                "GET", token);
        try {
            JSONArray list = new JSONArray(body(c));
            if (list.length() >= 100)
                throw new IllegalStateException("Слишком длинная история Issue; " +
                        "повторные отправки запрещены.");
            return list;
        } finally {c.disconnect();}
    }

    private static JSONObject putComment(long number, String text, String token) throws Exception {
        HttpURLConnection c = connection("/issues/" + number + "/comments",
                "POST", token);
        try {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = new JSONObject().put("body", text).toString()
                    .getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = c.getOutputStream()) {out.write(bytes);}
            if (c.getResponseCode() != 201)
                throw new IllegalStateException("GitHub отказал в записи комментария HTTP " +
                        c.getResponseCode());
            return new JSONObject(body(c));
        } finally {c.disconnect();}
    }

    private static int approvals(Mission mission, JSONArray comments) throws Exception {
        int count = 0;
        for (int i = 0; i < comments.length(); i++) {
            JSONObject item = comments.getJSONObject(i);
            if ("19wolfalone92".equals(item.getJSONObject("user").optString("login")) &&
                    mission.approval().equals(item.optString("body", "").trim()))
                count++;
        }
        return count;
    }

    /**
     * Requires two levels of consent: local Android confirmation followed by
     * this exact sha-bound owner-authored comment. Server does not treat an
     * Issue as an executable instruction without it.
     */
    public static String approve(long number, String uid,
                                 String project, String mode, String token) throws Exception {
        Mission issue = fetch(number, uid, token);
        if (!issue.project.equals(project) || !issue.mode.equals(mode))
            throw new IllegalArgumentException("Проект/режим изменён в GitHub Issue.");
        JSONArray history = comments(number, token);
        int already = approvals(issue, history);
        if (already >= 3)
            throw new IllegalStateException("Лимит: до трёх явных запусков одного Issue.");
        // Do not stack duplicate runs while existing mission attempt is pending.
        if (already > 0 && !hasFinished(issue, history, lastApprovalId(issue, history)))
            throw new IllegalStateException("Предыдущая попытка ещё не завершена. " +
                    "Проверь её в GitHub Issues. Для новой попытки при обрыве " +
                    "отправь новую явную команду через GitHub после проверки, " +
                    "чтобы не создать два параллельных запуска.");
        JSONObject posted = putComment(number, issue.approval(), token);
        return "Команда согласования опубликована в Issue #" + number +
                " (комментарий #" + posted.getLong("id") + ").\n" +
                "Только разрешённый GitHub workflow обработает её; " +
                "результат появится в Issue после проверки GitHub Actions.";
    }

    private static long lastApprovalId(Mission issue, JSONArray history) throws Exception {
        long id = 0;
        for (int i = 0; i < history.length(); i++) {
            JSONObject c = history.getJSONObject(i);
            if ("19wolfalone92".equals(c.getJSONObject("user").optString("login")) &&
                    issue.approval().equals(c.optString("body", "").trim()))
                id = Math.max(id, c.optLong("id", 0));
        }
        return id;
    }

    private static boolean hasFinished(Mission issue, JSONArray history, long approvalId)
            throws Exception {
        if (approvalId <= 0) return false;
        for (int i = 0; i < history.length(); i++) {
            JSONObject c = history.getJSONObject(i);
            String body = c.optString("body", "");
            if ("github-actions[bot]".equals(c.getJSONObject("user").optString("login")) &&
                    body.contains(issue.marker() + "approval=" + approvalId +
                            " event=finished -->"))
                return true;
        }
        return false;
    }

    public static String report(long number, String uid, String token) throws Exception {
        Mission issue = fetch(number, uid, token);
        JSONArray history = comments(number, token);
        StringBuilder summary = new StringBuilder();
        summary.append("🐺 GitHub Issue #").append(number).append(" — ")
                .append(issue.project).append("/").append(issue.mode).append("\n");
        int approvals = approvals(issue, history);
        summary.append("Явных одобрений: ").append(approvals).append("\n");
        boolean reserved = false, accepted = false, finished = false;
        String lastLink = "";
        for (int i = 0; i < history.length(); i++) {
            JSONObject c = history.getJSONObject(i);
            if (!"github-actions[bot]".equals(c.getJSONObject("user").optString("login")))
                continue;
            String content = c.optString("body", "");
            if (!content.contains(issue.marker())) continue;
            if (content.contains(" event=reserved -->")) reserved = true;
            if (content.contains(" event=accepted -->")) accepted = true;
            if (content.contains(" event=finished -->")) {
                finished = true;
                java.util.regex.Matcher link = java.util.regex.Pattern
                        .compile("https://github\\.com/19wolfalone92/WOLF-AI/actions/runs/[0-9]+")
                        .matcher(content);
                if (link.find()) lastLink = link.group();
            }
        }
        String state = approvals == 0 ? "ожидает твоего согласования" :
                finished ? "GitHub Actions завершён, проверь итоговый статус" :
                accepted ? "принят GitHub Actions; выполняется или ожидает сверки" :
                reserved ? "отправка зарезервирована; результат может быть неопределён" :
                "ожидает ближайшего запуска обработчика";
        summary.append("Состояние: ").append(state).append("\n");
        if (!lastLink.isEmpty()) summary.append("Проверенный запуск: ").append(lastLink).append("\n");
        summary.append("https://github.com/19wolfalone92/WOLF-AI/issues/").append(number);
        return summary.toString();
    }
}
