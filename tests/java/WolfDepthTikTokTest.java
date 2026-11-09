package ai.wolf.autopilot;

/** Routing checks: no account upload ever occurs from natural-language parsing. */
public final class WolfDepthTikTokTest {
    private static int checks;
    private static void eq(String phrase,WolfCommandRouter.Kind kind) {
        checks++;
        WolfCommandRouter.Decision decision=WolfCommandRouter.route(phrase);
        if(decision.kind != kind)
            throw new AssertionError(phrase+" -> "+decision.kind+" instead of "+kind);
    }
    public static void main(String[] args) {
        eq("Настрой глубину",WolfCommandRouter.Kind.DEPTH_SETTINGS);
        eq("Глубина 1",WolfCommandRouter.Kind.DEPTH_SETTINGS);
        eq("Глубина 2",WolfCommandRouter.Kind.DEPTH_SETTINGS);
        eq("Глубина 3",WolfCommandRouter.Kind.DEPTH_SETTINGS);
        eq("Глубина 4",WolfCommandRouter.Kind.DEPTH_SETTINGS);
        eq("Режимы глубины",WolfCommandRouter.Kind.DEPTH_SETTINGS);
        eq("Подумай глубже: проанализируй код",WolfCommandRouter.Kind.DEEP_LOCAL);
        eq("TikTok Studio",WolfCommandRouter.Kind.TIKTOK_STUDIO);
        eq("Тикток студио",WolfCommandRouter.Kind.TIKTOK_STUDIO);
        eq("Выложи видео в TikTok",WolfCommandRouter.Kind.TIKTOK_STUDIO);
        eq("Опубликуй ролик в тик ток",WolfCommandRouter.Kind.TIKTOK_STUDIO);
        eq("Загрузи ролик в TikTok без Метрикул",WolfCommandRouter.Kind.TIKTOK_STUDIO);
        eq("Запланируй публикацию через Metricool",WolfCommandRouter.Kind.TOOLS);
        eq("Открой календарь",WolfCommandRouter.Kind.DEVICE);
        System.out.println("PASS "+checks+" depth and direct TikTok chat routing cases.");
    }
}
