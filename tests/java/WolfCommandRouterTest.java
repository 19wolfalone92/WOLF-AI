package ai.wolf.autopilot;

/** Executable JDK routing contracts for Russian chat-first commands. */
public final class WolfCommandRouterTest {
    private static int checks;
    private WolfCommandRouterTest() {}

    private static void assertKind(String query, WolfCommandRouter.Kind expected) {
        WolfCommandRouter.Decision d = WolfCommandRouter.route(query);
        checks++;
        if (d.kind != expected)
            throw new AssertionError("Wrong route for '" + query + "': " + d.kind +
                    "; expected " + expected);
    }
    private static void check(boolean actual, String message) {
        checks++;
        if (!actual) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        assertKind("Привет", WolfCommandRouter.Kind.CHAT);
        assertKind("Объясни закон Ома", WolfCommandRouter.Kind.CHAT);
        assertKind("Сколько будет 2+2?", WolfCommandRouter.Kind.CHAT);
        assertKind("Что ты умеешь?", WolfCommandRouter.Kind.HELP);
        assertKind("Найди в интернете новости о спутниках",
                WolfCommandRouter.Kind.WEB_SEARCH);
        assertKind("Поищи в сети исследования по квантовым компьютерам",
                WolfCommandRouter.Kind.WEB_SEARCH);
        assertKind("Открой браузер", WolfCommandRouter.Kind.WEB_SEARCH);
        assertKind("Открой https://github.com", WolfCommandRouter.Kind.OPEN_URL);
        assertKind("Прочитай сайт https://example.com",
                WolfCommandRouter.Kind.READ_URL);
        assertKind("Открой YouTube", WolfCommandRouter.Kind.OPEN_URL);
        assertKind("Открой настройки телефона", WolfCommandRouter.Kind.DEVICE);
        assertKind("Покажи батарею", WolfCommandRouter.Kind.DEVICE);
        assertKind("Позвони +375 29 123 45 67", WolfCommandRouter.Kind.DEVICE);
        assertKind("Открой файлы", WolfCommandRouter.Kind.DEVICE);
        assertKind("Проверь код WOLF AI", WolfCommandRouter.Kind.PROJECT_TASK);
        assertKind("Исправь ошибки в приложении Shaira",
                WolfCommandRouter.Kind.PROJECT_TASK);
        assertKind("Собери APK", WolfCommandRouter.Kind.PROJECT_TASK);
        assertKind("Выложи видео в TikTok", WolfCommandRouter.Kind.TOOLS);
        assertKind("Создай видео в студии", WolfCommandRouter.Kind.TOOLS);
        assertKind("Открой инструменты", WolfCommandRouter.Kind.TOOLS);
        assertKind("Прочитай https://example.com", WolfCommandRouter.Kind.READ_URL);
        assertKind("Покажи сайт https://example.com", WolfCommandRouter.Kind.OPEN_URL);
        assertKind(" ", WolfCommandRouter.Kind.CHAT);

        WolfCommandRouter.Decision search =
                WolfCommandRouter.route("Найди в интернете космические новости");
        check(search.argument.contains("космические новости"),
                "Browser must receive the user's own search query.");
        check(WolfCommandRouter.route("Проверь код WOLF AI").kind !=
                WolfCommandRouter.Kind.DEVICE,
                "Code audits must never become phone actions.");
        check(WolfCommandRouter.route("Опубликуй ролик").kind !=
                WolfCommandRouter.Kind.PROJECT_TASK,
                "Publishing must never become a bogus GitHub audit.");
        System.out.println("PASS: " + checks + " Russian natural-language routing tests.");
    }
}
