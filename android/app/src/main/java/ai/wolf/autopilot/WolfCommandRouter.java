package ai.wolf.autopilot;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic and auditable natural-language routing; no untrusted shell. */
public final class WolfCommandRouter {
    public enum Kind {
        CHAT, WEB_SEARCH, OPEN_URL, READ_URL, RESEARCH, AUTONOMOUS_RESEARCH, DEEP_LOCAL, OFFLINE_MODEL, FREE_ONLY, CLOUD_AI, CLOUD_BROWSER, UI_AUTOMATION, DEVICE, PROJECT_TASK, TOOLS, HELP
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
        if (q.equals("включи бесплатный режим") ||
                q.equals("только бесплатный режим") ||
                q.equals("используй только бесплатные модели") ||
                q.equals("статус бюджета") ||
                q.equals("режим 0 рублей"))
            return new Decision(Kind.FREE_ONLY,text,
                    "Режим 0 ₽: блокировка облачных API и контроль расходов.");
        if (q.equals("улучши локальную модель") ||
                q.equals("бесплатные модели") ||
                q.equals("каталог моделей") ||
                q.equals("скачай qwen3") ||
                q.startsWith("скачай qwen3 ") ||
                q.equals("модели на телефон") ||
                q.equals("подключи локальную qwen3"))
            return new Decision(Kind.OFFLINE_MODEL,text,
                    "Скачать локальную Qwen3 по Wi-Fi без подписки.");
        if (q.startsWith("подумай глубже") ||
                q.startsWith("реши подробно") ||
                q.startsWith("режим эксперта") ||
                q.startsWith("разбери по шагам") ||
                q.startsWith("проверь своё решение") ||
                q.startsWith("проверь свое решение"))
            return new Decision(Kind.DEEP_LOCAL,text,
                    "Три реальных локальных этапа: план, ответ, критическая проверка.");
        if (contains(q, "что ты умеешь", "твои возможности", "инструкция",
                "как пользоваться", "помощь", "/help", "расскажи о себе"))
            return new Decision(Kind.HELP, "", "Показать возможности и ограничения.");

        if (q.startsWith("спроси облачный ии") ||
                q.startsWith("спроси облачный иИ".toLowerCase(Locale.ROOT)) ||
                q.startsWith("облачный ии:") ||
                q.startsWith("план облачного ии:") ||
                q.equals("настрой облачный ии") ||
                q.equals("подключи облачный ии"))
            return new Decision(Kind.CLOUD_AI,text,
                    "Отправить отдельно подтверждённый запрос в Hugging Face.");
        // Real remote CPU Chromium runs only after explicit user approval.
        // A public cloud browser is intentionally distinct from an authenticated
        // user-operated browser and never gets private cookies or passwords.
        if (q.startsWith("облачный браузер ") ||
                q.startsWith("запусти облачный браузер ") ||
                q.startsWith("исследуй сайт в облаке ") ||
                q.equals("статус облачного браузера"))
            return new Decision(Kind.CLOUD_BROWSER, text,
                    "GitHub Actions Chromium: публичный сайт без авторизации.");
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
        if (q.startsWith("проведи автономное исследование") ||
                q.startsWith("выполни исследование") ||
                q.startsWith("собери сравнительный отчёт") ||
                q.startsWith("собери сравнительный отчет"))
            return new Decision(Kind.AUTONOMOUS_RESEARCH,text,
                    "Автономно выполнить два публичных поиска, проверить страницы и составить отчёт.");
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
        // Only user-spoken explicit actions, never autonomous model tool calls.
        if (q.equals("управление приложениями") ||
                q.equals("включи управление приложениями") ||
                q.equals("статус управления") || q.equals("проверить управление") ||
                q.equals("покажи кнопки приложения") ||
                q.equals("посмотреть кнопки приложения") ||
                q.equals("список кнопок приложения") ||
                q.equals("отмени управление") || q.equals("останови управление") ||
                q.startsWith("нажми кнопку ") || q.startsWith("нажми в приложении ") ||
                q.startsWith("маршрут в приложении ") ||
                q.startsWith("выполни маршрут в приложении ") ||
                q.equals("пролистай приложение") || q.equals("прокрути приложение") ||
                q.equals("пролистай вниз в приложении"))
            return new Decision(Kind.UI_AUTOMATION, text,
                    "Согласованное действие навигации в другом приложении.");
        if (contains(q, "настройки телефона", "открой настройки", "настройки вайфай",
                "настройки wi-fi", "настройки bluetooth", "настройки блютуз",
                "открой камеру", "открой галерею", "открой фотографии",
                "открой файлы", "открой ютуб приложение", "включи будильник",
                "покажи будильник", "открой часы", "позвони", "набери номер",
                "напиши смс", "открой карты", "открой календарь",
                "покажи батарею", "заряд батареи", "информация о телефоне",
                "сколько памяти", "свободное место на телефоне",
                "поставь будильник", "будильник на", "разбуди в",
                "поставь таймер", "запусти таймер", "таймер на",
                "настройки яркости", "настройки экрана",
                "настройки звука", "громкость телефона",
                "настройки геолокации", "настройки местоположения",
                "настройки режима полёта", "режим полета",
                "настройки приложения wolf", "настройки wolf ai",
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
