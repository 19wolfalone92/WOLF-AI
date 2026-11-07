package ai.wolf.autopilot;

import android.util.Base64;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Strict WOLF-AI-only editor: read small approved source files and create
 * review-only PRs. No writes to main, no cross-project mutation, no merge.
 * The user must explicitly inspect and approve the complete replacement.
 */
public final class GithubCode {
    private static final String BASE = "https://api.github.com/repos/19wolfalone92/WOLF-AI";
    private static final int LIMIT = 14000;
    private GithubCode() {}

    public static final class FileData {
        public final String path, sha, content;
        public FileData(String path, String sha, String content) {
            this.path = path; this.sha = sha; this.content = content;
        }
    }

    private static void validatePath(String path) {
        if (path == null || !path.matches(
                "(?:wolf_ai/[A-Za-z0-9_-]+\\.py|" +
                "tests/test_wolf[A-Za-z0-9_-]*\\.py|" +
                "docs/[A-Za-z0-9_-]+\\.md)"))
            throw new IllegalArgumentException("Разрешены только wolf_ai/*.py, " +
                    "tests/test_wolf*.py и docs/*.md в WOLF-AI.");
    }

    private static HttpURLConnection request(String method, String path, String token) throws Exception {
        if (token == null || token.isEmpty())
            throw new IllegalStateException("Нужен GitHub PAT: Contents и Pull requests — Read and write на WOLF-AI.");
        HttpURLConnection con = (HttpURLConnection) new URL(BASE + path).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(12000);
        con.setReadTimeout(16000);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Authorization", "Bearer " + token);
        con.setRequestProperty("Accept", "application/vnd.github+json");
        con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        con.setRequestProperty("User-Agent", "WolfAI-Android/3.0");
        return con;
    }

    private static String read(HttpURLConnection con) throws Exception {
        InputStream stream = con.getResponseCode() >= 400 ? con.getErrorStream() : con.getInputStream();
        if (stream == null) return "";
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = in.read(buffer)) >= 0) {
                if (out.size() + length > 45000)
                    throw new IllegalStateException("Ответ GitHub слишком большой.");
                out.write(buffer, 0, length);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static JSONObject json(HttpURLConnection con, int expected) throws Exception {
        int status = con.getResponseCode();
        String payload = read(con);
        if (status != expected)
            throw new IllegalStateException("GitHub отказал (HTTP " + status +
                    "); проверь разрешения и неизменность файла.");
        return new JSONObject(payload);
    }

    private static JSONObject write(String path, String method,
                                    JSONObject requestBody, String token, int expected) throws Exception {
        HttpURLConnection con = request(method, path, token);
        try {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] bytes = requestBody.toString().getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = con.getOutputStream()) { out.write(bytes); }
            return json(con, expected);
        } finally {
            con.disconnect();
        }
    }

    public static FileData fetch(String path, String token) throws Exception {
        validatePath(path);
        HttpURLConnection con = request("GET", "/contents/" + path + "?ref=main", token);
        try {
            JSONObject payload = json(con, 200);
            if (!"file".equals(payload.optString("type")) ||
                    !"base64".equals(payload.optString("encoding")) ||
                    payload.optInt("size", LIMIT + 1) > LIMIT)
                throw new IllegalArgumentException("Нужен обычный небольшой текстовый файл.");
            String content = new String(
                    Base64.decode(payload.getString("content"), Base64.DEFAULT),
                    StandardCharsets.UTF_8);
            if (content.length() > LIMIT || content.indexOf('\u0000') >= 0)
                throw new IllegalArgumentException("Файл не является допустимым UTF-8 текстом.");
            return new FileData(path, payload.getString("sha"), content);
        } finally { con.disconnect(); }
    }

    private static void verifyText(String content) {
        if (content == null || content.trim().length() < 20 ||
                content.getBytes(StandardCharsets.UTF_8).length > LIMIT ||
                content.indexOf('\u0000') >= 0)
            throw new IllegalArgumentException("Изменения должны быть текстовыми, 20–14000 байт.");
        if (GithubBacklog.suspectedSecret(content))
            throw new IllegalArgumentException("Обнаружен возможный секрет. PR заблокирован.");
    }

    /** Create a user-reviewed PR with a single full-file replacement, no auto-merge. */
    public static String propose(FileData loaded, String newText, String token) throws Exception {
        if (loaded == null) throw new IllegalArgumentException("Сначала прочти файл из GitHub.");
        validatePath(loaded.path);
        verifyText(newText);
        if (loaded.content.equals(newText))
            throw new IllegalArgumentException("Нет изменений для Pull Request.");

        // Revalidate the contents SHA on main to prevent stomping newer changes.
        FileData now = fetch(loaded.path, token);
        if (!now.sha.equals(loaded.sha))
            throw new IllegalStateException("Файл изменился в main; перечитай его перед созданием PR.");

        HttpURLConnection base = request("GET", "/git/ref/heads/main", token);
        String mainCommit;
        try {
            mainCommit = json(base, 200).getJSONObject("object").getString("sha");
        } finally { base.disconnect(); }

        String branch = "wolf/android-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        write("/git/refs", "POST", new JSONObject()
                .put("ref", "refs/heads/" + branch)
                .put("sha", mainCommit), token, 201);

        String data = Base64.encodeToString(newText.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        write("/contents/" + loaded.path, "PUT", new JSONObject()
                .put("message", "fix(wolf): user-reviewed phone edit for " + loaded.path)
                .put("content", data)
                .put("sha", loaded.sha)
                .put("branch", branch), token, 200);

        JSONObject pr = write("/pulls", "POST", new JSONObject()
                .put("title", "WOLF Android suggestion: " + loaded.path)
                .put("head", branch)
                .put("base", "main")
                .put("body", "User-reviewed source change submitted from WOLF AI Android.\n" +
                        "One approved file: " + loaded.path + "\n" +
                        "This is NOT auto-merged or proven correct by the local model.\n" +
                        "Review the diff and ensure GitHub CI passes before merging."),
                token, 201);
        return pr.getString("html_url");
    }
}
