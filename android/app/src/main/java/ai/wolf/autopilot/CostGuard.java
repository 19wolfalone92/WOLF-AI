package ai.wolf.autopilot;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Free-only is mandatory by default. No automatic chargeable inference.
 * The owner may override the block separately inside cloud model settings.
 * This switch does not claim to police billing inside third-party accounts.
 */
public final class CostGuard {
    private static final String PREFS = "wolf_free_first_v1";
    private static final String KEY = "free_only";
    private CostGuard() {}
    public static boolean freeOnly(Context app) {
        return app.getApplicationContext()
                .getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean(KEY,true);
    }
    public static void setFreeOnly(Context app, boolean enabled) {
        SharedPreferences.Editor edit=app.getApplicationContext()
                .getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putBoolean(KEY,enabled);
        if(!edit.commit())throw new IllegalStateException("Не удалось сохранить режим расходов.");
    }
    public static void requireExternalInferenceEnabled(Context app) {
        if(freeOnly(app))throw new IllegalStateException(
                "Режим 0 ₽ активен: облачный API заблокирован, даже если токен " +
                "уже сохранён. Используй локальную Qwen или перейди в " +
                "☰ → Облачный ИИ для осознанного разрешения. " +
                "У Hugging Face есть небольшие бесплатные кредиты, " +
                "но доступность модели и биллинг зависят от аккаунта.");
    }
}
