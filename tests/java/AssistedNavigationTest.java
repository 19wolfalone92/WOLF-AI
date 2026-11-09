package ai.wolf.autopilot;

/** JVM tests of real deployed navigation policies and route contracts. */
public final class AssistedNavigationTest {
    private static int count;
    private static void check(boolean truth, String message) {
        count++;
        if(!truth)throw new AssertionError(message);
    }
    public static void main(String[] unused) {
        check(AssistivePolicy.allowedLabel("Профиль"), "safe visible button");
        check(AssistivePolicy.allowedLabel("Настройки"), "safe settings label");
        check(!AssistivePolicy.allowedLabel("Оплатить"), "no payments");
        check(!AssistivePolicy.allowedLabel("Отправить"), "no sending");
        check(!AssistivePolicy.allowedLabel("Опубликовать"), "no publishing");
        check(!AssistivePolicy.allowedLabel("Удалить"), "no deletion");
        check(!AssistivePolicy.allowedLabel("Password"), "no password");
        check(!AssistivePolicy.allowedLabel("ОК"), "short text ambiguous");
        check(!AssistivePolicy.allowedPackage("com.mybank.mobile", "ai.wolf.autopilot"),
                "banks denied");
        check(!AssistivePolicy.allowedPackage("ai.wolf.autopilot","ai.wolf.autopilot"),
                "self denied");
        check(!AssistivePolicy.allowedPackage("com.android.settings","ai.wolf.autopilot"),
                "system settings denied");
        check(AssistivePolicy.allowedPackage("com.example.reader","ai.wolf.autopilot"),
                "ordinary apps permitted after confirmation");
        check(AssistiveCommandParser.parse("Нажми кнопку Профиль").label.equals("Профиль"),
                "target extraction");
        check(AssistiveCommandParser.parse("Пролистай приложение").type.equals("scroll"),
                "scroll route");
        check(AssistiveCommandParser.parse(
                "Маршрут в приложении Профиль > Настройки").type.equals("sequence"),
                "Android sequence recognized");
        check(AssistiveCommandParser.parse(
                "Маршрут в приложении Профиль > Настройки").label.equals(
                        "Профиль > Настройки"), "exact step labels preserved");
        check(WolfCommandRouter.route("Маршрут в приложении Профиль > Настройки").kind ==
                WolfCommandRouter.Kind.UI_AUTOMATION, "route chat sequence");
        check(AssistiveCommandParser.parse("Отмени управление").type.equals("cancel"),
                "cancel route");
        check(WolfCommandRouter.route("Нажми кнопку Профиль").kind ==
                WolfCommandRouter.Kind.UI_AUTOMATION, "chat UI route");
        check(WolfCommandRouter.route("Пролистай приложение").kind ==
                WolfCommandRouter.Kind.UI_AUTOMATION, "chat scroll route");
        check(WolfCommandRouter.route("Опубликуй видео").kind !=
                WolfCommandRouter.Kind.UI_AUTOMATION, "social writes cannot route UI");
        check(BrowserActionPolicy.safeOrigin("https://github.com/repo").equals(
                "https://github.com"), "origin preservation");
        check(!BrowserActionPolicy.allowedLabel("Оплатить"), "web blocked payment");
        String preview=BrowserActionPolicy.script("https://github.com/repo",
                "Issues",false);
        String click=BrowserActionPolicy.script("https://github.com/repo",
                "Issues",true);
        check(preview.contains("location.origin"), "origin verified");
        check(preview.contains("closest('form')"), "forms excluded");
        check(preview.contains("matches.length!==1"), "ambiguous elements rejected");
        check(preview.contains("if(!false)"), "preview no click");
        check(click.contains("matches[0].click()"), "fixed click");
        check(!preview.contains("eval("), "no arbitrary eval");
        check(!preview.contains("document.cookie"), "cookies never inspected");
        java.util.List<String> flow=BrowserFlowPlan.parse(
                "Профиль > Настройки > Помощь");
        check(flow.size()==3, "up to four pre-approved steps");
        try {
            BrowserFlowPlan.parse("Профиль > Оплатить");
            throw new AssertionError("Unsafe step accepted");
        } catch (IllegalArgumentException expected) { count++; }
        try {
            BrowserFlowPlan.parse("A > B > C > D > E");
            throw new AssertionError("Unbounded steps accepted");
        } catch (IllegalArgumentException expected) { count++; }
        try {
            BrowserActionPolicy.safeOrigin("http://github.com/");
            throw new AssertionError("http not denied");
        } catch (IllegalArgumentException expected) { count++; }
        try {
            BrowserActionPolicy.safeOrigin("https://www.paypal.com/");
            throw new AssertionError("finance host not denied");
        } catch (IllegalArgumentException expected) { count++; }
        try {
            AssistiveCommandParser.parse("Нажми кнопку Подтвердить");
            throw new AssertionError("unsafe click label not denied");
        } catch (IllegalArgumentException expected) { count++; }
        check(WolfCommandRouter.route("Облачный браузер https://example.com").kind ==
                WolfCommandRouter.Kind.CLOUD_BROWSER, "cloud browser route");
        check(WolfCommandRouter.route("Статус облачного браузера").kind ==
                WolfCommandRouter.Kind.CLOUD_BROWSER, "cloud status route");
        CloudBrowserCommand.Plan cloud=CloudBrowserCommand.parse(
                "Облачный браузер https://example.com/path шаги: Профиль > Настройки");
        check(cloud.url.equals("https://example.com/path"), "exact cloud URL");
        check(cloud.steps.contains("Профиль"), "exact approved cloud steps");
        try {
            CloudBrowserCommand.parse("Облачный браузер https://localhost/admin");
            throw new AssertionError("Local cloud URL accepted");
        } catch(IllegalArgumentException expected) { count++; }
        try {
            CloudBrowserCommand.parse(
                    "Облачный браузер https://example.com?access_token=123");
            throw new AssertionError("Tokenized public URL accepted");
        } catch(IllegalArgumentException expected) { count++; }
        check(WolfCommandRouter.route("Проведи автономное исследование энергетика").kind ==
                WolfCommandRouter.Kind.AUTONOMOUS_RESEARCH,
                "user-approved public research agent route");
        check(WolfCommandRouter.route("Спроси облачный ИИ: дай план").kind ==
                WolfCommandRouter.Kind.CLOUD_AI,
                "explicit cloud model route");
        System.out.println("PASS: " + count + " assisted browser / Android route checks.");
    }
}
