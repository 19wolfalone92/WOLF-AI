package ai.wolf.autopilot;

/** JVM execution proves no arbitrary model URLs, hashes and intent routing. */
public final class WolfFreeFirstTest {
    private static int count=0;
    private static void check(boolean ok,String label) {
        count++;
        if(!ok)throw new AssertionError(label);
    }
    private static void reject(Runnable r) {
        count++;
        try { r.run(); }
        catch(IllegalArgumentException expected) {return;}
        throw new AssertionError("Unknown model accepted.");
    }
    public static void main(String[] args) {
        OfflineModelCatalog.Entry a=OfflineModelCatalog.Entry.QWEN3_17;
        OfflineModelCatalog.Entry c=OfflineModelCatalog.Entry.QWEN3_4;
        check(OfflineModelCatalog.trusted(a),"Qwen3 1.7B pinned entry");
        check(OfflineModelCatalog.trusted(c),"Qwen3 4B pinned entry");
        check(a.url.equals("https://huggingface.co/ggml-org/Qwen3-1.7B-GGUF/resolve/main/Qwen3-1.7B-Q4_K_M.gguf"),
                "trusted upstream 1.7B only");
        check(c.url.equals("https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/Qwen3-4B-Q4_K_M.gguf"),
                "trusted upstream 4B only");
        check(a.sha256.equals("d2387ca2dbfee2ffabce7120d3770dadca0b293052bc2f0e138fdc940d9bc7b5"),
                "1.7B hash unchanged");
        check(c.sha256.equals("7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5"),
                "4B hash unchanged");
        reject(()->OfflineModelCatalog.get("https://evil.com/weights.gguf"));
        reject(()->OfflineModelCatalog.get("../files"));
        check(WolfCommandRouter.route("Бесплатные модели").kind ==
                WolfCommandRouter.Kind.OFFLINE_MODEL,"open free catalog from chat");
        check(WolfCommandRouter.route("Улучши локальную модель").kind ==
                WolfCommandRouter.Kind.OFFLINE_MODEL,"model upgrade");
        check(WolfCommandRouter.route("Включи бесплатный режим").kind ==
                WolfCommandRouter.Kind.FREE_ONLY,"zero-fee mode");
        check(WolfCommandRouter.route("Статус бюджета").kind ==
                WolfCommandRouter.Kind.FREE_ONLY,"budget status");
        check(WolfCommandRouter.route("Подумай глубже: объясни индуктивность").kind ==
                WolfCommandRouter.Kind.DEEP_LOCAL,"three-pass offline reasoning");
        check(WolfCommandRouter.route("Спроси облачный ИИ: привет").kind ==
                WolfCommandRouter.Kind.CLOUD_AI,"explicit cloud remains separate");
        check(WolfCommandRouter.route("Проведи автономное исследование: электрика").kind ==
                WolfCommandRouter.Kind.AUTONOMOUS_RESEARCH,"existing read-only tools intact");
        System.out.println("PASS: "+count+" executable free-first model selection and routing tests");
    }
}
