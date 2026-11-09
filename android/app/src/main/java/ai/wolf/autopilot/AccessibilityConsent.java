package ai.wolf.autopilot;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Explicit, separate consent for a general-purpose Android assistant that uses
 * AccessibilityService. Android's system permission alone is NOT consent to
 * WOLF's optional UI-reading/navigation feature.
 *
 * V2 is deliberate: a changed disclosure must be accepted again.
 */
public final class AccessibilityConsent {
    private static final String PREFS = "wolf_accessibility_consent";
    private static final String KEY = "accessibility_disclosure_v2";
    private AccessibilityConsent() {}

    public static boolean accepted(Context context) {
        return context.getApplicationContext().getSharedPreferences(
                PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false);
    }

    public static void accept(Context context) {
        persist(context, true);
    }

    public static void revoke(Context context) {
        persist(context, false);
        WolfAccessibilityService.cancel();
    }

    private static void persist(Context context, boolean value) {
        SharedPreferences preferences=context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if(!preferences.edit().putBoolean(KEY,value).commit())
            throw new IllegalStateException("Не удалось сохранить согласие Accessibility.");
    }

    /**
     * The precise disclosure shown on first use, not hidden in Settings/Guide.
     * No unsupported assertion that the service technically cannot read data.
     */
    public static String disclosure() {
        return "Для ограниченного управления чужими приложениями WOLF " +
                "запрашивает разрешение Android «Специальные возможности».\n\n" +
                "⚠️ ЧТО ДОСТУПНО: система позволяет такой службе видеть " +
                "содержимое и названия элементов на экранах других приложений, " +
                "включая потенциально личную информацию. Это чувствительный доступ.\n\n" +
                "КАК WOLF ИСПОЛЬЗУЕТ ДОСТУП: только после твоей отдельной " +
                "команды и подтверждения, до 45 секунд; максимум четыре " +
                "заданных шага в одном разрешённом приложении. " +
                "WOLF отфильтровывает поля пароля, запрещённые кнопки и " +
                "опасные приложения. Он может показать краткий список " +
                "видимых безопасных кнопок.\n\n" +
                "ХРАНЕНИЕ И ПЕРЕДАЧА: собранные надписи остаются в RAM " +
                "на устройстве, не отправляются в облако, не включаются " +
                "в журналы GitHub и не сохраняются как снимки экрана.\n\n" +
                "КОНТРОЛЬ: разрешение можно отключить в настройках Android, " +
                "а согласие отозвать в меню WOLF. Без согласия управление " +
                "другими приложениями недоступно.\n\n" +
                "Согласен использовать эту НЕОБЯЗАТЕЛЬНУ возможность?";
    }
}
