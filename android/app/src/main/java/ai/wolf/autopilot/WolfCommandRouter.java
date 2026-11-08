package ai.wolf.autopilot;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic and auditable natural-language routing; no untrusted shell. */
public final class WolfCommandRouter {
    public enum Kind {
        CHAT, WEB_SEARCH, OPEN_URL, READ_URL, RESEARCH, DEVICE, PROJECT_TASK, TOOLS, HELP
    }
    public static final class Decision {
        public final Kind kind;
        public final String argument;
        public final String explanation;
        Decision(Kind kind, String argument, String explanation) {
            this.kind = kind; this.argument = argument; this.explanation = explanation;
        }
    }
    private static final Pattern URL = Pattern.compile(
            "https://[A-Za-z0-9][^\\s<>\"']{0,380}", Pattern.CASE_INSENSITIVE);
    private WolfCommandRouter() {}

    public static Decision route(String raw) {
        if (raw == null || raw.trim().isEmpty())
            return new Decision(Kind.CHAT, "", "Напиши вопрос.");
        String text = raw.trim();
        String q = text.toLowerCase(Locale.ROOT);
        if (contains(q, "что ты умеешь", "твои возможности", "инструкция",
                "как пользоваться", "помощь", "/help", "расскажи о себе"))
            return new Decision(Kind.HELP, "", "Показать возможности и ограничения.");

        Matcher url = URL.matcher(text);
        if (url.find()) {
            String value = url.group().replaceAll("[),.;!?]+$", "");
            if (contains(q, "прочитай", "изучи сайт", "изучи страницу",
                    "проанализируй сайт", "проанализируй страницу", "суммируй",
                    "резюмируй", "что на странице"))
                return new Decision(Kind.READ_URL, value, "Прочитать публичную веб-страницу.");
            if (contains(q, "открой", "перейди", "браузер", "покажи сайт", "посети"))
                return new Decision(Kind.OPEN_URL, value, "Открыть страницу в браузере.");
        }
        if (contains(q, "ютуб", "youtube", "тикток", "tiktok", "гугл", "google",
                "github.com", "гитхаб сайт") && contains(q, "открой", "зайди", "перейди")) {
            String target = q.contains("ютуб") || q.contains("youtube") ?
                    "https://www.youtube.com/" :
                    q.contains("тикток") || q.contains("tiktok") ?
                    "https://www.tiktok.com/" :
                    q.contains("github") || q.contains("гитхаб") ?
                    "https://github.com/19wolfalone92" : "https://www.google.com/";
            return new Decision(Kind.OPEN_URL, target, "Открыть сайт в браузере.");
        }
        if (contains(q, "найди в интернете", "поиск в интернете", "поиск в сети",
                "поищи в интернете", "поищи в сети", "погугли",
                "найди в браузере", "открой браузер", "интернет поиск",
                "web search", "search the web", "открой интернет")) {
            String query = text.replaceFirst("(?iu)^\\s*(найди|поищи|погугли|поиск|открой)\\s*(в интернете|в сети|в браузере|браузер|интернет)?\\s*[:—-]?\\s*", "").trim();
            if (contains(q, "открой браузер", "открой интернет"))
                query = "";
            return new Decision(Kind.WEB_SEARCH, query, "Поиск в браузере.");
        }
        // Explicit deep-research intent triggers a multi-step, source-grounded
        // report with separate consent, never silent crawling of private chats.
        if (contains(q, "исследуй тему", "сделай исследование",
                "подготовь исследование", "подготовь отчёт",
                "подготовь отчет", "сравни источники", "изучи подробно тему",
                "сделай глубокий поиск"))
            return new Decision(Kind.RESEARCH, text,
                    "Исследовать публичные веб-источники и подготовить отчёт.");
        if (contains(q, "опубликуй видео", "опубликуй ролик",
                "выложи видео", "выложи ролик", "запости видео",
                "запланируй публикацию", "загрузи ролик в тикток",
                "сделай видео", "создай видео", "собери видеоролик",
                "настрой metricool", "подключи metricool",
                "открой инструменты", "открой студию",
                "подключи гитхаб", "настрой github")) {
            return new Decision(Kind.TOOLS, text, "Открыть настройки и видеостудию.");
        }
        if (contains(q, "настройки телефона", "открой настройки", "настройки вайфай",
                "настройки wi-fi", "настройки bluetooth", "настройки блютуз",
                "открой камеру", "открой галерею", "открой фотографии",
                "открой файлы", "открой ютуб приложение", "включи будильник",
                "покажи будильник", "открой часы", "позвони", "набери номер",
                "напиши смс", "открой карты", "открой календарь",
                "покажи батарею", "заряд батареи", "информация о телефоне",
                "открой wifi", "включи вайфай"))
            return new Decision(Kind.DEVICE, text, "Выполнить действие через Android.");
        // Remote code checks are NEVER confused with arbitrary device or social writes.
        if (contains(q, "проверь проект", "проверь код", "проверь репозитор",
                "запусти тесты", "исправь ошибки", "проверь ошибки приложения",
                "собери apk", "собери приложение", "найди ошибки в коде",
                "проверь ci", "создай пулл реквест", "запусти github")) {
            return new Decision(Kind.PROJECT_TASK, text,
                    "Подготовить проверку проекта в GitHub после подтверждения.");
        }
        return new Decision(Kind.CHAT, text, "Ответить локальной моделью.");
    }

    private static boolean contains(String source, String... phrases) {
        for (String phrase : phrases) if (source.contains(phrase)) return true;
        return false;
    }
}
