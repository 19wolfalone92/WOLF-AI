package ai.wolf.autopilot;

/** JVM-only tests; no network/model/Android runtime required. */
public final class WolfHybridBrainTest {
    private static int checks;
    private static void yes(boolean truth, String name) {
        checks++;
        if(!truth)throw new AssertionError(name);
    }
    private static void reject(Runnable value) {
        checks++;
        try { value.run(); }
        catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Unsafe value was accepted");
    }
    public static void main(String[] args) {
        yes(CloudBrainPolicy.model("openai/gpt-oss-20b:cheapest")
                .equals("openai/gpt-oss-20b:cheapest"),"cloud model ID");
        yes(CloudBrainPolicy.question("Расскажи о диодах").equals(
                "Расскажи о диодах"),"cloud input");
        reject(()->CloudBrainPolicy.question("Hi"));
        reject(()->CloudBrainPolicy.question("token=hf_abcdefghi123456789"));
        reject(()->CloudBrainPolicy.model("http://fake/api"));
        reject(()->CloudBrainPolicy.model("../secrets"));
        reject(()->CloudBrainPolicy.model("model.without-org"));
        yes(AuthenticatedFormPolicy.field("Поиск").equals("Поиск"),"safe label");
        yes(AuthenticatedFormPolicy.value("Обычный текст").equals("Обычный текст"),
                "safe text");
        reject(()->AuthenticatedFormPolicy.field("Password"));
        reject(()->AuthenticatedFormPolicy.value("hf_abcdefghij123456789012345"));
        String preview=AuthenticatedFormPolicy.script(
                "https://example.org/settings","Поиск","сказка",false);
        yes(preview.contains("location.origin"),"fixed HTTPS origin");
        yes(preview.contains("matches.length!==1"),"one exact field");
        yes(preview.contains("closest"),"no read from input");
        yes(preview.contains("return 'ready:1'"),"preview only");
        yes(!preview.contains("dispatchEvent"),"no preview write");
        String fill=AuthenticatedFormPolicy.script(
                "https://example.org/settings","Поиск","сказка",true);
        yes(fill.contains("dispatchEvent"),"explicit fill has input event");
        yes(fill.contains("field-filled-not-submitted"),"never sends form");
        yes(!fill.contains(".submit("),"no form submit");
        yes(!fill.contains("document.cookie"),"no cookie access");
        reject(()->AuthenticatedFormPolicy.script(
                "http://example.org","Поиск","сказка",false));
        System.out.println("PASS: "+checks+" hybrid AI and safe draft tests.");
    }
}
