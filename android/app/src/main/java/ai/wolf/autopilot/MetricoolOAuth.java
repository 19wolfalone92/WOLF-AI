package ai.wolf.autopilot;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * OAuth 2.1 Public Native Client with DCR + PKCE S256 for Metricool MCP.
 * Separate, user-approved identity; never imports ChatGPT OAuth or an API key.
 * DCR authorization is not guaranteed by remote provider: errors are surfaced.
 */
public final class MetricoolOAuth {
    private static final String CLIENT_REGISTER = "https://app.metricool.com/oauth/register";
    private static final String AUTH = "https://app.metricool.com/oauth/authorize";
    private static final String TOKEN = "https://app.metricool.com/oauth/token";
    private static final String REDIRECT = "ai.wolf.autopilot://oauth/metricool";
    private static final String RESOURCE = "https://ai.metricool.com/mcp";
    private static final String KEY = "WOLF_METRICOOL_OAUTH_V1";
    private static final String VAULT = "wolf_metricool_oauth_v1";

    private final SharedPreferences prefs;
    private final SecureRandom random = new SecureRandom();

    public MetricoolOAuth(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(VAULT, Context.MODE_PRIVATE);
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        KeyStore.Entry item = store.getEntry(KEY, null);
        if (item instanceof KeyStore.SecretKeyEntry)
            return ((KeyStore.SecretKeyEntry) item).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }

    private void putSecret(JSONObject value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = c.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        prefs.edit()
                .putString("token_cipher", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("token_nonce", Base64.encodeToString(c.getIV(), Base64.NO_WRAP))
                .apply();
    }

    private JSONObject getSecret() throws Exception {
        String cipher = prefs.getString("token_cipher", "");
        String nonce = prefs.getString("token_nonce", "");
        if (cipher.isEmpty() || nonce.isEmpty()) return new JSONObject();
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(),
                new GCMParameterSpec(128, Base64.decode(nonce, Base64.DEFAULT)));
        byte[] value = c.doFinal(Base64.decode(cipher, Base64.DEFAULT));
        return new JSONObject(new String(value, StandardCharsets.UTF_8));
    }

    public boolean connected() {
        return prefs.contains("token_cipher") && prefs.contains("token_nonce");
    }

    public void disconnect() {
        prefs.edit().clear().apply();
        // This is local deletion only. User can also revoke OAuth in Metricool.
    }

    private static String randomString(SecureRandom random) {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static String percent(String value) throws Exception {
        return java.net.URLEncoder.encode(value, "UTF-8");
    }

    private static HttpURLConnection open(String endpoint) throws Exception {
        if (!endpoint.equals(CLIENT_REGISTER) && !endpoint.equals(TOKEN))
            throw new IllegalArgumentException("Недопустимый адрес OAuth.");
        HttpURLConnection con = (HttpURLConnection) new URL(endpoint).openConnection();
        con.setRequestMethod("POST");
        con.setConnectTimeout(12000);
        con.setReadTimeout(16000);
        con.setInstanceFollowRedirects(false);
        con.setDoOutput(true);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("User-Agent", "WOLF-AI-Android/3.1");
        return con;
    }

    private static JSONObject post(String endpoint, String contentType, String payload) throws Exception {
        HttpURLConnection con = open(endpoint);
        try {
            con.setRequestProperty("Content-Type", contentType);
            byte[] data = payload.getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = con.getOutputStream()) {out.write(data);}
            int status = con.getResponseCode();
            InputStream input = status >= 400 ? con.getErrorStream() : con.getInputStream();
            if (status < 200 || status >= 300) {
                if (input != null) input.close();
                throw new IllegalStateException("Metricool OAuth: HTTP " + status +
                        ". Возможно, регистрация нового мобильного клиента не разрешена.");
            }
            if (input == null) throw new IllegalStateException("Пустой ответ Metricool OAuth.");
            try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > 40000)
                        throw new IllegalStateException("Превышен лимит ответа OAuth.");
                    out.write(buf, 0, n);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
            }
        } finally {con.disconnect();}
    }

    private String clientId() throws Exception {
        String existing = prefs.getString("client_id", "");
        if (!existing.isEmpty()) return existing;
        // DCR registers this app's own identity as a native public OAuth client.
        // No client_secret is stored/bundled. Fail if provider disallows DCR.
        JSONObject request = new JSONObject();
        request.put("client_name", "WOLF AI 3.1 Android");
        request.put("redirect_uris", new org.json.JSONArray().put(REDIRECT));
        request.put("grant_types", new org.json.JSONArray()
                .put("authorization_code").put("refresh_token"));
        request.put("response_types", new org.json.JSONArray().put("code"));
        request.put("token_endpoint_auth_method", "none");
        request.put("application_type", "native");
        request.put("scope", "mcp:read mcp:write");
        JSONObject response = post(CLIENT_REGISTER, "application/json; charset=utf-8",
                request.toString());
        if (!"none".equals(response.optString("token_endpoint_auth_method", "none")) ||
                response.optString("client_secret", "").length() > 0)
            throw new IllegalStateException("Metricool требует конфиденциальный client_secret; " +
                    "в публичном APK его хранить нельзя.");
        String id = response.optString("client_id", "");
        if (id.isEmpty() || id.length() > 300)
            throw new IllegalStateException("OAuth client_id не получен.");
        prefs.edit().putString("client_id", id).apply();
        return id;
    }

    /** Performs DCR. Browser is opened ONLY after this returns successfully. */
    public Intent beginAuthorization() throws Exception {
        String id = clientId();
        String verifier = randomString(random) + randomString(random);
        String state = randomString(random);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String challenge = Base64.encodeToString(
                digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
        // Persist only a short-lived PKCE verifier; not the actual access token.
        prefs.edit().putString("pending_state", state)
                .putString("pending_verifier", verifier)
                .putLong("pending_created", System.currentTimeMillis()).apply();
        String link = AUTH + "?response_type=code" +
                "&client_id=" + percent(id) +
                "&redirect_uri=" + percent(REDIRECT) +
                "&scope=" + percent("mcp:read mcp:write") +
                "&resource=" + percent(RESOURCE) +
                "&state=" + percent(state) +
                "&code_challenge=" + percent(challenge) +
                "&code_challenge_method=S256";
        return new Intent(Intent.ACTION_VIEW, Uri.parse(link))
                .addCategory(Intent.CATEGORY_BROWSABLE);
    }

    private void clearPending() {
        prefs.edit().remove("pending_state").remove("pending_verifier")
                .remove("pending_created").apply();
    }

    public String complete(Uri uri) throws Exception {
        if (uri == null || !"ai.wolf.autopilot".equals(uri.getScheme()) ||
                !"oauth".equals(uri.getHost()) || !"/metricool".equals(uri.getPath()))
            throw new IllegalArgumentException("OAuth redirect недействителен.");
        String state = uri.getQueryParameter("state");
        if (state == null || !state.equals(prefs.getString("pending_state", "")) ||
                state.isEmpty()) throw new IllegalStateException("OAuth state не совпал.");
        long created = prefs.getLong("pending_created", 0);
        if (created < 1 || Math.abs(System.currentTimeMillis() - created) > 10 * 60_000L)
            throw new IllegalStateException("OAuth авторизация просрочена.");
        String failure = uri.getQueryParameter("error");
        if (failure != null) {
            clearPending();
            throw new IllegalStateException("Авторизация отклонена: " + failure);
        }
        String code = uri.getQueryParameter("code");
        String verifier = prefs.getString("pending_verifier", "");
        if (code == null || code.length() < 5 || verifier.isEmpty())
            throw new IllegalStateException("Не получены OAuth code или PKCE verifier.");
        JSONObject response = post(TOKEN, "application/x-www-form-urlencoded; charset=utf-8",
                "grant_type=authorization_code" +
                        "&client_id=" + percent(clientId()) +
                        "&code=" + percent(code) +
                        "&redirect_uri=" + percent(REDIRECT) +
                        "&code_verifier=" + percent(verifier) +
                        "&resource=" + percent(RESOURCE));
        clearPending();
        saveTokenResponse(response, null);
        return "✅ Metricool MCP OAuth подтверждён. " +
                "WOLF может прочитать список доступных инструментов.";
    }

    private void saveTokenResponse(JSONObject response, String existingRefresh) throws Exception {
        String access = response.optString("access_token", "");
        if (access.length() < 10 || access.length() > 5000)
            throw new IllegalStateException("Metricool не выдал корректный OAuth токен.");
        JSONObject value = new JSONObject();
        value.put("access_token", access);
        value.put("refresh_token", response.optString("refresh_token",
                existingRefresh == null ? "" : existingRefresh));
        long lifetime = response.optLong("expires_in", 3600);
        if (lifetime < 60 || lifetime > 31_536_000) lifetime = 3600;
        value.put("expires_at", System.currentTimeMillis() + lifetime * 1000);
        putSecret(value);
    }

    public String accessToken() throws Exception {
        JSONObject token = getSecret();
        String value = token.optString("access_token", "");
        if (value.isEmpty()) throw new IllegalStateException("Metricool MCP не подключён.");
        if (System.currentTimeMillis() + 90_000L <
                token.optLong("expires_at", 0)) return value;
        String refresh = token.optString("refresh_token", "");
        if (refresh.isEmpty()) {
            disconnect();
            throw new IllegalStateException("Metricool OAuth истёк: подключись повторно.");
        }
        JSONObject response = post(TOKEN, "application/x-www-form-urlencoded; charset=utf-8",
                "grant_type=refresh_token" +
                        "&client_id=" + percent(clientId()) +
                        "&refresh_token=" + percent(refresh) +
                        "&resource=" + percent(RESOURCE));
        saveTokenResponse(response, refresh);
        return getSecret().getString("access_token");
    }
}
