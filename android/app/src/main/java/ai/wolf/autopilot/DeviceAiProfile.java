package ai.wolf.autopilot;

import java.util.Locale;

/**
 * Public rules, no Android dependency so device-specific decisions are
 * actually tested on JVM. All inputs are measured locally in the app;
 * nothing is transmitted to GitHub/HF or a cloud LLM.
 */
public final class DeviceAiProfile {
    public static final long GB=1_000_000_000L;
    private DeviceAiProfile() {}

    public enum Tier { COMPACT, BALANCED, POWERFUL }

    public static final class Plan {
        public final Tier tier;
        public final OfflineModelCatalog.Entry recommended;
        public final int preferredStages;
        public final int contextTokens;
        public final boolean lowMemory;
        public final String explanation;
        Plan(Tier tier, OfflineModelCatalog.Entry recommended, int stages,
             int ctx, boolean lowMemory, String why) {
            this.tier=tier;this.recommended=recommended;
            this.preferredStages=stages;this.contextTokens=ctx;
            this.lowMemory=lowMemory;this.explanation=why;
        }
        public String summary() {
            return "Рекомендация WOLF: "+recommended.title+
                    " · "+preferredStages+" проход(а) для сложных задач"+
                    " · контекст до "+contextTokens+" токенов. "+
                    explanation;
        }
    }

    /** RAM and free storage are bytes, not GB. No fabricated device values. */
    public static Plan select(long totalRam,long availableRam,long freeBytes) {
        if(totalRam<4*GB || availableRam<1_500_000_000L ||
                freeBytes<1_700_000_000L) {
            return new Plan(Tier.COMPACT,
                    OfflineModelCatalog.Entry.QWEN35_08,1,2048,true,
                    "Мало доступной памяти/места: сначала закрой тяжёлые " +
                    "приложения; при недостатке места оставь встроенную 0.5B.");
        }
        if(totalRam>=11*GB && availableRam>=3_800_000_000L &&
                freeBytes>=6_300_000_000L) {
            return new Plan(Tier.POWERFUL,
                    OfflineModelCatalog.Entry.QWEN35_4,3,4096,false,
                    "Версия с 12 ГБ RAM может пробовать 4B Q4; " +
                    "скорость и стабильность всё равно проверяются на телефоне.");
        }
        if(freeBytes<2_900_000_000L || availableRam<2_500_000_000L) {
            return new Plan(Tier.COMPACT,
                    OfflineModelCatalog.Entry.QWEN35_08,1,2048,true,
                    "Для 2B пока недостаточно свободной RAM или места. " +
                    "Попробуй 0.8B Q4, а не 4B.");
        }
        return new Plan(Tier.BALANCED,
                OfflineModelCatalog.Entry.QWEN35_2,2,3072,false,
                "Основной баланс разговорного качества, RAM и CPU для " +
                "Samsung Galaxy S23 Ultra с 8/12 ГБ. Начни с 2B Q4.");
    }

    public static String formatBytes(long bytes) {
        return bytes<0?"неизвестно":
                String.format(Locale.ROOT,"%.1f ГБ",bytes/(double)GB);
    }

    /** A warning, not an unconditional ban. Owner can still select a model. */
    public static String warning(OfflineModelCatalog.Entry entry, Plan plan) {
        if(entry==null || plan==null)return "Недостаточно данных для оценки.";
        if(entry==OfflineModelCatalog.Entry.QWEN35_4 ||
                entry==OfflineModelCatalog.Entry.QWEN3_4) {
            if(plan.tier!=Tier.POWERFUL)
                return "⚠️ 4B может не поместиться в свободную RAM; " +
                        "возможны перегрев, медленная работа и перезапуск приложения.";
            return "⚠️ 4B допускается только как тест: проверь скорость, " +
                    "нагрев и стабильность прежде чем включать глубокий режим.";
        }
        return "Оценка по памяти, не тест реальной скорости модели.";
    }
}
