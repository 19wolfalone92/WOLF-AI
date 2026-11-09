package ai.wolf.autopilot;

import android.content.Context;
import android.content.SharedPreferences;

/** Configurable CPU-only reasoning. Never schedules background LLM calls. */
public final class ThinkingDepth {
    public static final String PREFS="wolf_depth_v16";
    public static final int MIN_STEPS=1, MAX_STEPS=4;
    public static final int MIN_TOKENS=128, MAX_TOKENS=768;

    public static final class Settings {
        public final int steps;
        public final int tokens;
        public final boolean automatic;
        public Settings(int steps,int tokens,boolean automatic) {
            this.steps=clampSteps(steps);this.tokens=clampTokens(tokens);
            this.automatic=automatic;
        }
        public String label() {
            String title=steps==1?"Быстрый":steps==2?"Сбалансированный":
                    steps==3?"Глубокий":"Экспертный";
            return title+" · "+steps+" проход(а) · "+tokens+" токенов"+
                    (automatic?" · для обычных вопросов":" · только по команде");
        }
    }

    private ThinkingDepth() {}
    public static int clampSteps(int value) {
        return Math.max(MIN_STEPS,Math.min(MAX_STEPS,value));
    }
    public static int clampTokens(int value) {
        return Math.max(MIN_TOKENS,Math.min(MAX_TOKENS,value));
    }
    public static Settings preset(int level) {
        switch(clampSteps(level)) {
            case 1:return new Settings(1,256,false);
            case 2:return new Settings(2,384,false);
            case 3:return new Settings(3,512,false);
            default:return new Settings(4,768,false);
        }
    }
    public static Settings load(Context context) {
        SharedPreferences p=context.getApplicationContext()
                .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        return new Settings(p.getInt("steps",3),p.getInt("tokens",512),
                p.getBoolean("automatic",false));
    }
    public static void save(Context context,Settings settings) {
        if(settings==null)throw new IllegalArgumentException("Выбери глубину.");
        boolean ok=context.getApplicationContext()
                .getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .edit().putInt("steps",settings.steps).putInt("tokens",settings.tokens)
                .putBoolean("automatic",settings.automatic).commit();
        if(!ok)throw new IllegalStateException("Не удалось сохранить настройки.");
    }
    public static Settings updateSteps(Settings old,int steps) {
        Settings preset=preset(steps);
        return new Settings(preset.steps,preset.tokens,old!=null&&old.automatic);
    }
}
