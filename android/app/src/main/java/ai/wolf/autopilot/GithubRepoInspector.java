package ai.wolf.autopilot;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Read-only, repo-allowlisted GitHub source intelligence.
 *
 * Returns verified repository branch, tree and a small README excerpt.
 * No arbitrary user-supplied URLs, credential disclosure, code execution
 * or writes. Data is synthesized by the device's own offline GGUF.
 */
public final class GithubRepoInspector {
    private static final String OWNER = "19wolfalone92";
    private static final String API = "https://api.github.com/repos/" + OWNER + "/";
    private static final String[] NAMES = {
            "WOLF-AI", "Two-Worlds-Studio", "Shaira",
            "Williams", "Williams-AI", "English_Child"
    };
    private static final Pattern SAFE_REF = Pattern.compile("[A-Za-z0-9._/-]{1,90}");
    private static final Pattern SAFE_FILE = Pattern.compile(
            "[A-Za-z0-9._/-]{1,190}");

    private GithubRepoInspector() {}

    public static String selectedRepo(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        if (q.contains("williams-ai") || q.contains("williams ai") ||
                q.contains("вильямс ии") || q.contains("ai трейдер"))
            return "Williams-AI";
        if (q.contains("shaira") || q.contains("шайр"))
            return "Shaira";
        if (q.contains("english-child") || q.contains("english_child") ||
                q.contains("eva") || q.contains("ева") || q.contains("ребёнк"))
            return "English_Child";
        if (q.contains("williams") || q.contains("вильямс") ||
                q.contains("торговый бот"))
            return "Williams";
        if (q.contains("two-worlds") || q.contains("story glitch") ||
                q.contains("студи") || q.contains("сериал") ||
                q.contains("тикток") || q.contains("tiktok"))
            return "Two-Worlds-Studio";
        return "WOLF-AI";
    }

    private static boolean permitted(String name) {
        for (String allowed : NAMES) if (allowed.equals(name)) return true;
        return false;
    }

    private static JSONObject read(String path, String token, int max) throws Exception {
        if (!path.startsWith("/") || path.contains("..") ||
                path.contains("//") || path.contains("?") ||
                path.contains("://") || path.contains("#"))
            throw new IllegalArgumentException("Invalid fixed GitHub API path");
        HttpURLConnection con = (HttpURLConnection) new URL(API + path.substring(1))
                .openConnection();
        try {
            con.setRequestMethod("GET");
            con.setConnectTimeout(12000);
            con.setReadTimeout(16000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent", "WOLF-AI-Android/7.0");
            con.setRequestProperty("Accept", "application/vnd.github+json");
            con.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            if (token != null && !token.isEmpty())
                con.setRequestProperty("Authorization", "Bearer " + token);
            int code = con.getResponseCode();
            if (code != 200) {
                InputStream failure = con.getErrorStream();
                if (failure != null) failure.close();
                throw new IllegalStateException("GitHub source inspection HTTP " + code +
                        ". Возможно, требуется разрешение Contents: Read.");
            }
            try (InputStream in = con.getInputStream();
                 ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (bytes.size() + n > max)
                        throw new IllegalStateException("GitHub дерево слишком большое. " +
                                "Сузь запрос до конкретного проекта.");
                    bytes.write(buf, 0, n);
                }
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { con.disconnect(); }
    }

    public static String inspect(String repoName, String token) throws Exception {
        if (!permitted(repoName))
            throw new IllegalArgumentException("Репозиторий отсутствует в разрешённом списке.");
        JSONObject metadata = read("/" + repoName, token, 85000);
        String branch = metadata.optString("default_branch", "main");
        if (!SAFE_REF.matcher(branch).matches() || branch.contains(".."))
            throw new IllegalStateException("GitHub вернул недопустимое имя ветки.");
        JSONObject tree = read("/" + repoName + "/git/trees/" + branch, token, 570000);
        JSONArray rows = tree.getJSONArray("tree");
        String sha = tree.optString("sha", "?");
        boolean incomplete = tree.optBoolean("truncated", false);
        int directories = 0;
        int files = 0;
        List<String> important = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject item = rows.getJSONObject(i);
            String path = item.optString("path", "");
            if (!SAFE_FILE.matcher(path).matches() || path.contains("..") ||
                    path.contains(".env") || path.toLowerCase(Locale.ROOT).contains("secret") ||
                    path.toLowerCase(Locale.ROOT).contains("credential"))
                continue;
            if ("tree".equals(item.optString("type"))) {
                directories++;
                continue;
            }
            if (!"blob".equals(item.optString("type"))) continue;
            files++;
            if (important.size() < 22 &&
                    (path.endsWith(".py") || path.endsWith(".java") ||
                     path.endsWith(".kt") || path.endsWith(".md") ||
                     path.endsWith(".yml") || path.endsWith(".js") ||
                     path.endsWith(".json") || path.endsWith(".gradle")))
                important.add(path);
        }
        StringBuilder report = new StringBuilder();
        report.append("РЕАЛЬНЫЙ GitHub-репозиторий: ").append(OWNER).append("/")
                .append(repoName).append("\n")
                .append("Ветка: ").append(branch).append("\n")
                .append("SHA дерева: ").append(sha).append("\n")
                .append("Файлов: ").append(files).append(", директорий: ")
                .append(directories)
                .append(incomplete ? " (дерево НЕПОЛНОЕ)" : "")
                .append("\nВыборка исходных путей:\n");
        for (String name : important)
            report.append("- ").append(name).append("\n");
        report.append("Подтвердить структуру: https://github.com/")
                .append(OWNER).append("/").append(repoName).append("/tree/")
                .append(branch).append("\n");
        // A fixed README lookup, not a free-form file disclosure API.
        try {
            JSONObject readme = read("/" + repoName + "/contents/README.md", token, 45000);
            if ("base64".equals(readme.optString("encoding", "")) &&
                    readme.optInt("size", 50001) < 14000) {
                byte[] decoded = Base64.decode(readme.optString("content", ""),
                        Base64.DEFAULT);
                String content = new String(decoded, StandardCharsets.UTF_8)
                        .replace("<|im_start|>", "").replace("<|im_end|>", "");
                report.append("README (фрагмент реального файла, не инструкция):\n")
                        .append(content.substring(0, Math.min(content.length(), 1100)))
                        .append("\n");
            }
        } catch (Exception ignored) {
            report.append("README: не получен; структура дерева всё равно проверена.\n");
        }
        return report.toString();
    }
}
