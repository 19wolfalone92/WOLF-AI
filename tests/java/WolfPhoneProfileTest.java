package ai.wolf.autopilot;

/** Hardware policy regression: safe profile, official SHA-pinned GGUF, Russian routing. */
public final class WolfPhoneProfileTest {
    private static int count;
    private static void check(boolean value,String what) {
        count++;if(!value)throw new AssertionError(what);
    }
    public static void main(String[] args) {
        long g=DeviceAiProfile.GB;
        DeviceAiProfile.Plan balanced=DeviceAiProfile.select(8*g,4*g,18*g);
        check(balanced.tier==DeviceAiProfile.Tier.BALANCED,"8GB balanced");
        check(balanced.recommended==OfflineModelCatalog.Entry.QWEN35_2,
                "8GB should select Qwen3.5 2B");
        check(balanced.contextTokens==3072,"8GB memory-efficient context");
        DeviceAiProfile.Plan powerful=DeviceAiProfile.select(12*g,5*g,25*g);
        check(powerful.tier==DeviceAiProfile.Tier.POWERFUL,"12GB powerful");
        check(powerful.recommended==OfflineModelCatalog.Entry.QWEN35_4,
                "12GB selected Qwen3.5 4B only with headroom");
        check(powerful.contextTokens==4096,"12GB larger context");
        DeviceAiProfile.Plan constrained=DeviceAiProfile.select(12*g,2*g,20*g);
        check(constrained.tier==DeviceAiProfile.Tier.COMPACT,"low free RAM");
        check(constrained.recommended==OfflineModelCatalog.Entry.QWEN35_08,
                "low headroom chooses Qwen3.5 0.8B");
        check(constrained.contextTokens==2048,"low headroom context");
        DeviceAiProfile.Plan disk=DeviceAiProfile.select(8*g,4*g,g);
        check(disk.recommended==OfflineModelCatalog.Entry.QWEN35_08,
                "disk limited compact");
        for(OfflineModelCatalog.Entry e:OfflineModelCatalog.Entry.values()) {
            check(OfflineModelCatalog.trusted(e),"unsigned model "+e.name());
            check(e.url.startsWith("https://huggingface.co/"),"official host");
        }
        check(OfflineModelCatalog.Entry.QWEN35_08.sha256.equals(
                "57d1997790d1744fba5b40a7317df71ea5e2acee28c47e78f0cce39c0703f8cf"),
                "verified 0.8B SHA");
        check(OfflineModelCatalog.Entry.QWEN35_2.sha256.equals(
                "aaf42c8b7c3cab2bf3d69c355048d4a0ee9973d48f16c731c0520ee914699223"),
                "verified 2B SHA");
        check(OfflineModelCatalog.Entry.QWEN35_4.sha256.equals(
                "00fe7986ff5f6b463e62455821146049db6f9313603938a70800d1fb69ef11a4"),
                "verified 4B SHA");
        check(WolfCommandRouter.route("Проверь параметры моего телефона").kind==
                WolfCommandRouter.Kind.PHONE_PROFILE,"device chat command");
        check(WolfCommandRouter.route("Подбери ИИ под телефон").kind==
                WolfCommandRouter.Kind.PHONE_PROFILE,"device choice command");
        check(WolfCommandRouter.route("Привет").kind==
                WolfCommandRouter.Kind.CHAT,"free topic chat remains");
        System.out.println("PASS "+count+" verified hardware/model/chat contracts.");
    }
}
