package ai.wolf.autopilot;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Only explicit public, no-login jobs; never arbitrary model tool output. */
public final class CloudBrowserCommand {
    private static final Pattern URL = Pattern.compile("https://[^\\s<>\"']{1,970}",
            Pattern.CASE_INSENSITIVE);
    public static final class Plan {
        public final String url, steps;
        Plan(String url, String steps) { this.url = url; this.steps = steps; }
    }
    private CloudBrowserCommand() {}

    public static Plan parse(String text) {
        if (text == null || text.length() > 1400)
            throw new IllegalArgumentException("Запрос слишком длинный.");
        Matcher matcher = URL.matcher(text);
        if (!matcher.find())
            throw new IllegalArgumentException("Укажи публичный HTTPS-адрес.");
        String url = matcher.group().replaceAll("[),.;!?]+$", "");
        String suffix = text.substring(matcher.end()).trim();
        if (!suffix.isEmpty() &&
                !suffix.toLowerCase(Locale.ROOT).startsWith("шаги:"))
            throw new IllegalArgumentException("После адреса укажи только «шаги: ...»");
        String steps = suffix.isEmpty() ? "" : suffix.substring(5).trim();
        if (!steps.isEmpty()) BrowserFlowPlan.parse(steps);
        try {
            URI address = new URI(url);
            if (!"https".equalsIgnoreCase(address.getScheme()) ||
                    address.getHost() == null || address.getUserInfo() != null ||
                    address.getPort() != -1 || address.getFragment() != null ||
                    url.length() > 1000)
                throw new IllegalArgumentException("Недопустимый HTTPS URL.");
            String host = address.getHost().toLowerCase(Locale.ROOT);
            if (!host.matches("[a-z0-9.-]{4,250}") || !host.contains(".") ||
                    host.contains("..") || host.endsWith(".local") ||
                    host.endsWith(".internal") || host.endsWith(".test") ||
                    host.equals("localhost") ||
                    host.matches("[0-9.]+") ||
                    (address.getRawQuery() != null &&
                    address.getRawQuery().matches(
                            "(?is).*(password|token|secret|api[_-]?key|auth|code=|session).*")))
                throw new IllegalArgumentException("Публичный адрес недопустим или содержит секрет.");
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "Нужен обычный публичный HTTPS-домен без паролей и локальных адресов.");
        }
        return new Plan(url, steps);
    }
}
