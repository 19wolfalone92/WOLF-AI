package ai.wolf.autopilot;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.util.Locale;

/**
 * Narrow, user-driven Streamable HTTP MCP client. Only reads brands or
 * creates ONE explicitly confirmed post. No model-generated tool calls.
 * Supports JSON and SSE data frames, exact OAuth issuer allowlist.
 */
public final class MetricoolMcp {
    private static final String URL = "https://ai.metricool.com/mcp";
    private final MetricoolOAuth auth;
    private final SharedPreferences prefs;
    private String session;
    private int nextId = 1;

    public MetricoolMcp(Context context, MetricoolOAuth auth) {
        this.auth = auth;
        prefs = context.getApplicationContext()
                .getSharedPreferences("wolf_metricool_publish_receipts_v1", Context.MODE_PRIVATE);
    }

    private JSONObject send(String method, JSONObject params, boolean notification) throws Exception {
        JSONObject request = new JSONObject().put("jsonrpc", "2.0")
                .put("method", method);
        if (!notification) request.put("id", nextId++);
        if (params != null) request.put("params", params);
        HttpURLConnection con = (HttpURLConnection) new URL(URL).openConnection();
        try {
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            con.setInstanceFollowRedirects(false);
            con.setConnectTimeout(12000);
            con.setReadTimeout(30000);
            con.setRequestProperty("User-Agent", "WOLF-AI-Android/3.1");
            con.setRequestProperty("Authorization", "Bearer " + auth.accessToken());
            con.setRequestProperty("Accept", "application/json, text/event-stream");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("MCP-Protocol-Version", "2025-06-18");
            if (session != null && !session.isEmpty())
                con.setRequestProperty("Mcp-Session-Id", session);
            byte[] bytes = request.toString().getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream out = con.getOutputStream()) {out.write(bytes);}
            int status = con.getResponseCode();
            if (status < 200 || status >= 300) {
                InputStream err = con.getErrorStream();
                if (err != null) err.close();
                throw new IllegalStateException("Metricool MCP ответил HTTP " + status +
                        ". Проверь OAuth разрешения или совместимость протокола.");
            }
            String newSession = con.getHeaderField("Mcp-Session-Id");
            if (newSession != null && !newSession.isEmpty() && newSession.length() < 512)
                session = newSession;
            InputStream stream = con.getInputStream();
            if (stream == null || notification) {
                if (stream != null) stream.close();
                return new JSONObject();
            }
            String mime = con.getContentType();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder data = new StringBuilder();
                String line;
                JSONObject latest = null;
                while ((line = reader.readLine()) != null) {
                    if (data.length() > 350000)
                        throw new IllegalStateException("Ответ MCP слишком большой.");
                    if (mime != null && mime.contains("text/event-stream")) {
                        if (line.startsWith("data:")) {
                            String raw = line.substring(5).trim();
                            if (!raw.isEmpty() && raw.charAt(0) == '{') {
                                JSONObject parsed = new JSONObject(raw);
                                if (parsed.has("result") || parsed.has("error")) {
                                    latest = parsed;
                                    break;
                                }
                            }
                        }
                        data.append(line).append('\n');
                    } else {
                        data.append(line).append('\n');
                    }
                }
                if (latest != null) return latest;
                if (data.length() == 0)
                    throw new IllegalStateException("MCP не вернул ответ.");
                JSONObject result = new JSONObject(data.toString());
                if (result.has("error")) throw new IllegalStateException("Metricool MCP вернул ошибку.");
                return result;
            }
        } finally {
            con.disconnect();
        }
    }

    private void handshake() throws Exception {
        JSONObject params = new JSONObject()
                .put("protocolVersion", "2025-06-18")
                .put("capabilities", new JSONObject())
                .put("clientInfo", new JSONObject()
                        .put("name", "WOLF-AI-Android")
                        .put("version", "3.1.0"));
        JSONObject response = send("initialize", params, false);
        if (!response.has("result"))
            throw new IllegalStateException("Metricool MCP initialize не подтвердился.");
        send("notifications/initialized", null, true);
    }

    private JSONObject findTool(String[] candidates) throws Exception {
        JSONObject response = send("tools/list", new JSONObject(), false);
        JSONArray items = response.getJSONObject("result").getJSONArray("tools");
        for (String wanted : candidates) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject tool = items.getJSONObject(i);
                if (wanted.equals(tool.optString("name"))) return tool;
            }
        }
        throw new IllegalStateException("Metricool не предоставил ожидаемый MCP-инструмент. " +
                "Версия сервера отличается; изменения не отправлены.");
    }

    private JSONObject call(JSONObject tool, JSONObject args) throws Exception {
        JSONObject response = send("tools/call", new JSONObject()
                .put("name", tool.getString("name")).put("arguments", args), false);
        JSONObject result = response.optJSONObject("result");
        if (result == null || result.optBoolean("isError", false))
            throw new IllegalStateException("Metricool отклонил действие или ответил ошибкой.");
        return result;
    }

    /** This is real connected account read, not a fake 'connected' status. */
    public String brands() throws Exception {
        handshake();
        JSONObject tool = findTool(new String[]{
                "getBrandSettings", "get_brands", "getBrands", "get_brand_settings"});
        JSONObject result = call(tool, new JSONObject());
        String text = result.toString();
        // Never expose raw account OAuth data or endless report blobs in UI.
        if (text.length() > 10000) text = text.substring(0, 10000) + "\n[обрезано]";
        return "Подключение к Metricool MCP подтверждено.\n" + text;
    }

    public static final class VideoPost {
        public final String brandId, url, caption, dateIso, timezone, network;
        public final boolean autoPublish;
        public VideoPost(String brandId, String url, String caption, String dateIso,
                         String timezone, String network, boolean autoPublish) {
            this.brandId=brandId; this.url=url; this.caption=caption;
            this.dateIso=dateIso; this.timezone=timezone; this.network=network;
            this.autoPublish=autoPublish;
        }
    }

    private static void validate(VideoPost p) throws Exception {
        if (p == null || p.brandId == null || !p.brandId.matches("[0-9]{1,20}"))
            throw new IllegalArgumentException("Необходимо числовое ID бренда Metricool.");
        if (p.url == null || p.url.length() > 1500 ||
                !p.url.startsWith("https://") ||
                p.url.contains("@") || p.url.contains("localhost") ||
                p.url.matches("(?i).*https://(?:127\\.|10\\.|192\\.168\\.|172\\.(?:1[6-9]|2[0-9]|3[01])\\.).*"))
            throw new IllegalArgumentException("Укажи публичный HTTPS URL MP4 без логинов/паролей.");
        String path = new URL(p.url).getPath().toLowerCase(Locale.ROOT);
        if (!path.endsWith(".mp4"))
            throw new IllegalArgumentException("Ссылка должна вести на публичный .mp4 файл.");
        if (p.caption == null || p.caption.length() > 1800)
            throw new IllegalArgumentException("Подпись должна быть не длиннее 1800 символов.");
        if (!("tiktok".equals(p.network) || "instagram".equals(p.network) ||
                "youtube".equals(p.network) || "facebook".equals(p.network)))
            throw new IllegalArgumentException("Неподдерживаемая социальная сеть.");
        if (p.timezone == null || !("Europe/Minsk".equals(p.timezone) ||
                "Europe/Amsterdam".equals(p.timezone) ||
                "UTC".equals(p.timezone)))
            throw new IllegalArgumentException("Требуется поддерживаемый часовой пояс.");
        OffsetDateTime when = OffsetDateTime.parse(p.dateIso);
        if (!when.toInstant().isAfter(Instant.now().plusSeconds(5 * 60)) ||
                when.toInstant().isAfter(Instant.now().plusSeconds(365L * 24L * 3600L)))
            throw new IllegalArgumentException("Дата должна быть от 5 минут до 365 дней в будущем.");
        if (p.autoPublish && p.caption.trim().isEmpty())
            throw new IllegalArgumentException("Для автоматической публикации нужна подпись.");
    }

    private String fingerprint(VideoPost post) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = (post.brandId + "\n" + post.url + "\n" +
                post.network + "\n" + post.dateIso + "\n" + post.caption).getBytes(
                        StandardCharsets.UTF_8);
        byte[] hash = digest.digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte b : hash) value.append(String.format(Locale.ROOT, "%02x", b & 255));
        return value.toString();
    }

    /**
     * Only called AFTER explicit per-post approval.
     * Never retry a write automatically; uncertain timeout = check Metricool Planner.
     */
    public String schedule(VideoPost post) throws Exception {
        validate(post);
        String key = fingerprint(post);
        if (prefs.getBoolean("sent_" + key, false))
            throw new IllegalStateException("Похожая публикация уже отправлена. " +
                    "Проверь планировщик Metricool, чтобы избежать дубликата.");
        handshake();
        JSONObject tool = findTool(new String[]{"createScheduledPost", "create_schedule_post"});
        JSONObject schema = tool.optJSONObject("inputSchema");
        if (schema == null || !schema.has("properties"))
            throw new IllegalStateException("Metricool изменил схему публикации; запрос не отправлен.");
        JSONObject props = schema.getJSONObject("properties");
        if (!(props.has("date") && props.has("blogId") && props.has("info")))
            throw new IllegalStateException("Схема публикации несовместима; запрос не отправлен.");
        OffsetDateTime datetime = OffsetDateTime.parse(post.dateIso);
        String local = datetime.atZoneSameInstant(ZoneId.of(post.timezone))
                .toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
        JSONObject info = new JSONObject()
                .put("text", post.caption)
                .put("media", new JSONArray().put(post.url))
                .put("providers", new JSONArray().put(new JSONObject().put("network", post.network)))
                .put("draft", !post.autoPublish)
                .put("autoPublish", post.autoPublish)
                .put("publicationDate", new JSONObject()
                        .put("dateTime", local).put("timezone", post.timezone));
        switch (post.network) {
            case "tiktok":
                info.put("tiktokData", new JSONObject()
                        .put("privacyOption", "PUBLIC_TO_EVERYONE")
                        .put("isAigc", true).put("title", post.caption.substring(0, Math.min(90, post.caption.length()))));
                break;
            case "instagram":
                info.put("instagramData", new JSONObject().put("type", "REEL")
                        .put("isAiGenerated", true));
                break;
            case "youtube":
                info.put("youtubeData", new JSONObject()
                        .put("title", post.caption.isEmpty() ? "WOLF video" :
                                post.caption.substring(0, Math.min(95, post.caption.length())))
                        .put("type", "short").put("madeForKids", false)
                        .put("isAiGeneratedContent", true)
                        .put("privacy", post.autoPublish ? "public" : "private"));
                break;
            case "facebook":
                info.put("facebookData", new JSONObject().put("type", "REEL"));
                break;
            default: throw new IllegalStateException("Unsupported provider");
        }
        JSONObject args = new JSONObject().put("date", post.dateIso)
                .put("blogId", post.brandId).put("info", info.toString());
        JSONObject result = call(tool, args);
        // MCP result may have tool-specific errors inside text content; don't
        // declare success if returned text documents a rejection.
        JSONArray content = result.optJSONArray("content");
        if (content == null || content.length() == 0)
            throw new IllegalStateException("Metricool не подтвердил публикацию. " +
                    "Проверь Planner, прежде чем повторять.");
        String human = result.toString();
        if (human.length() > 5000) human = human.substring(0, 5000);
        prefs.edit().putBoolean("sent_" + key, true).apply();
        return "Metricool MCP принял ответ на запрос.\n" + human +
                "\nПРОВЕРЬ точный статус поста в Metricool. " +
                "Запрос нельзя считать подтверждённой публикацией в TikTok.";
    }
}
