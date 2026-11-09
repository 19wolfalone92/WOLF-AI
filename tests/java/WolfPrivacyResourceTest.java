package ai.wolf.autopilot;

/** Actual JVM regression of new privacy, resource, Accessibility and task IDs. */
public final class WolfPrivacyResourceTest {
    private static int count;
    private static void check(boolean valid,String name){
        count++;
        if(!valid)throw new AssertionError(name);
    }
    public static void main(String[] unused) {
        String token="github_pat_ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        String msg="Привет, мой ключ "+token+" и пароль password:myverysecret";
        String hidden=SensitiveText.forStorage(msg);
        check(!hidden.contains(token),"github key redacted");
        check(!hidden.contains("myverysecret"),"password assignment redacted");
        check(hidden.contains("Привет"),"normal message preserved");
        check(!SensitiveText.containsCredential("Я забыл пароль от сайта."),
                "non-credential conversation should remain intact");
        check(SensitiveText.containsCredential("Bearer abcde12345abcd"),
                "HTTP bearer key detected");
        check(!SensitiveText.forStorage("hf_abcdefghijklmnop")
                        .contains("abcdefghijklmnop"),"HF token blocked");
        check(!SensitiveText.forStorage("sk-abcdef123456789012345")
                        .contains("abcdef123456789012345"),"LLM key blocked");
        check(!SensitiveText.forStorage("\"access_token\":\"abCDefgh1\"")
                        .contains("abCDefgh1"),"JSON token blocked");
        check(!AssistivePolicy.isSensitiveField(false,
                "android.widget.Button",0),"button not password field");
        check(AssistivePolicy.isSensitiveField(true,
                "android.widget.TextView",0),"native password flag");
        check(AssistivePolicy.isSensitiveField(false,
                "android.widget.EditText",0x81),"textPassword input");
        check(AssistivePolicy.isSensitiveField(false,
                "android.widget.EditText",0x91),"textVisiblePassword input");
        check(AssistivePolicy.isSensitiveField(false,
                "android.widget.EditText",0xe1),"textWebPassword input");
        check(AssistivePolicy.isSensitiveField(false,
                "android.widget.EditText",0x12),"numberPin input");
        check(!AssistivePolicy.isSensitiveField(false,
                "android.widget.EditText",0x01),"regular typed message");
        LocalResourcePolicy normal=LocalResourcePolicy.evaluate(
                4*LocalResourcePolicy.GB,false,0,60,false);
        check(normal.level==LocalResourcePolicy.Level.NORMAL,"normal");
        check(normal.steps(4)==4,"full optional depth");
        LocalResourcePolicy low=LocalResourcePolicy.evaluate(
                2*LocalResourcePolicy.GB,false,1,70,false);
        check(low.level==LocalResourcePolicy.Level.CONSERVE,"RAM conserve");
        check(low.steps(4)==1,"high-depth gated");
        check(!low.useImported(2*LocalResourcePolicy.GB),"2B fallback");
        check(low.useImported(500_000_000),"0.5B permitted");
        LocalResourcePolicy hot=LocalResourcePolicy.evaluate(
                5*LocalResourcePolicy.GB,false,4,90,true);
        check(hot.level==LocalResourcePolicy.Level.CRITICAL,"thermal critical even charging");
        check(!hot.useImported(500_000_000),"thermal forces bundled model");
        LocalResourcePolicy battery=LocalResourcePolicy.evaluate(
                5*LocalResourcePolicy.GB,false,0,8,false);
        check(battery.level==LocalResourcePolicy.Level.CRITICAL,"critical battery");
        check(LocalResourcePolicy.evaluate(5*LocalResourcePolicy.GB,
                false,0,8,true).level==LocalResourcePolicy.Level.NORMAL,
                "charging exempts battery-only critical");
        check(CommandParser.validProject("williams-ai"),"known project");
        check(!CommandParser.validProject("https://unsafe.example"),"invalid project");
        check(CommandParser.validMode("test") && CommandParser.validMode("audit"),
                "known modes");
        check(!CommandParser.validMode("publish"),"no remote publish mode");
        check(CommandParser.validTaskUid("w1234567890abcdef1234567890abcdef"),
                "UID format");
        check(!CommandParser.validTaskUid("manual"),"manual not a mobile UID");
        check(SafeNetworkRetry.retryGetStatus(429,0),"HTTP 429 read retry");
        check(SafeNetworkRetry.retryGetStatus(503,1),"HTTP 503 read retry");
        check(!SafeNetworkRetry.retryGetStatus(400,0),"bad request not retried");
        check(!SafeNetworkRetry.retryGetStatus(503,2),"bounded attempts");
        check(SafeNetworkRetry.delayMs(0)==250 &&
                SafeNetworkRetry.delayMs(1)==500,"exponential GET-only backoff");
        System.out.println("PASS "+count+" resource/privacy/contract policy cases");
    }
}
