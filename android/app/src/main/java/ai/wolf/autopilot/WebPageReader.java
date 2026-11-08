package ai.wolf.autopilot;

import android.text.Html;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * User-approved one-page PUBLIC HTTPS reader. Read-only, no cookies, no POST,
 * no browser session, no arbitrary redirects, bounded HTML/text excerpt.
 * Never sends phone files, local notes, history or account credentials.
 */
public final class WebPageReader {
    private WebPageReader() {}
    public static final class Page {
        public final String url, title, text;
        Page(String url, String title, String text) {
            this.url = url; this.title = title; this.text = text;
        }
    }

    public static String validate(String address) throws Exception {
        if (address == null || address.length() < 12 || address.length() > 400)
            throw new IllegalArgumentException("Адрес должен быть публичным HTTPS URL.");
        URI uri = new URI(address.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                uri.getUserInfo() != null || uri.getHost() == null ||
                uri.getPort() != -1 || uri.getFragment() != null)
            throw new IllegalArgumentException("Поддерживается только HTTPS без логина, порта и якоря.");
        String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.ROOT);
        if (!host.matches("[a-z0-9-]{1,63}(\\.[a-z0-9-]{1,63})+") ||
                host.endsWith(".local") || host.endsWith(".internal") ||
                host.endsWith(".localhost") || host.endsWith(".test") ||
                host.contains(".."))
            throw new IllegalArgumentException("Можно читать только публичные домены.");
        String raw = uri.getRawQuery();
        if (raw != null && raw.matches("(?is).*(token|api[_-]?key|password|auth|secret|code=).*"))
            throw new IllegalArgumentException("Запрос содержит возможный секрет. Не отправляй его.");
        for (InetAddress ip : InetAddress.getAllByName(host)) {
            byte[] ipBytes = ip.getAddress();
            if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() ||
                    ip.isLinkLocalAddress() || ip.isSiteLocalAddress() ||
                    ip.isMulticastAddress() ||
                    (ipBytes.length == 16 && ((ipBytes[0] & 0xfe) == 0xfc)))
                throw new IllegalArgumentException("Локальные адреса и домашняя сеть закрыты.");
        }
        return uri.toASCIIString();
    }

    public static Page read(String source) throws Exception {
        String url = validate(source);
        HttpsURLConnection con = (HttpsURLConnection) new URL(url).openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(11000);
            con.setInstanceFollowRedirects(false);
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "WOLF-AI-Public-Reader/10.0");
            con.setRequestProperty("Accept", "text/html,text/plain");
            con.setRequestProperty("Cookie", "");
            int status = con.getResponseCode();
            if (status >= 300 && status < 400)
                throw new IllegalStateException("Сайт перенаправляет на другой адрес. " +
                        "Открой его вручную в браузере WOLF.");
            if (status != 200)
                throw new IllegalStateException("Страница недоступна: HTTPS " + status);
            String type = con.getContentType();
            if (type == null || (!type.toLowerCase(Locale.ROOT).contains("text/html") &&
                    !type.toLowerCase(Locale.ROOT).contains("text/plain")))
                throw new IllegalStateException("Сайт не вернул текстовую страницу.");
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            try (InputStream in = con.getInputStream()) {
                byte[] block = new byte[8192];
                int n;
                while ((n = in.read(block)) >= 0) {
                    if (buf.size() + n > 140000)
                        throw new IllegalStateException("Страница слишком большая. " +
                                "Открой её в браузере.");
                    buf.write(block, 0, n);
                }
            }
            String html = buf.toString(StandardCharsets.UTF_8.name());
            String title = url;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                    "(?is)<title[^>]*>(.*?)</title>").matcher(html);
            if (m.find()) {
                title = Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY)
                        .toString().trim();
                if (title.length() > 100) title = title.substring(0, 100);
            }
            String cleaned = html.replaceAll("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>", " ")
                    .replaceAll("(?is)<!--.*?-->", " ")
                    .replaceAll("(?is)<head[^>]*>.*?</head>", " ");
            String plain = type.toLowerCase(Locale.ROOT).contains("text/html") ?
                    Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString() : cleaned;
            plain = plain.replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
            if (plain.length() < 30)
                throw new IllegalStateException("Страница без доступного текстового содержимого. " +
                        "Возможно, сайт требует JavaScript или авторизацию.");
            return new Page(url, title,
                    plain.substring(0, Math.min(2600, plain.length())));
        } finally {
            con.disconnect();
        }
    }
}
