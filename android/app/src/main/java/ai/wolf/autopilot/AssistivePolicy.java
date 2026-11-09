package ai.wolf.autopilot;

import java.util.Locale;

/** Pure Java policy contracts for opt-in external app navigation. */
public final class AssistivePolicy {
    private AssistivePolicy() {}
    public static String normalize(CharSequence value) {
        if (value == null) return "";
        return value.toString().trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
    public static boolean allowedLabel(String value) {
        String s = normalize(value);
        if (s.length() < 3 || s.length() > 70 || s.contains("://")) return false;
        String[] disallowed = {
                "парол", "password", "пин", "pin code", "otp", "2fa",
                "отправ", "send", "опубли", "publish", "post now",
                "удал", "delete", "remove account", "купить", "buy",
                "оплат", "checkout", "pay", "перевод денег", "transfer money",
                "подтверд", "confirm", "разреш", "allow access",
                "установ", "install", "подписать", "sign transaction",
                "withdraw", "вывести", "кошел", "wallet",
                "войти", "sign in", "login", "log in", "logout", "log out",
                "согласен", "agree", "accept", "submit", "сохранить"
        };
        for (String marker : disallowed) if (s.contains(marker)) return false;
        return true;
    }
    /**
     * Android InputType variation values, here in pure Java for JVM tests.
     * Never read text from a password input or inspect/navigate its screen.
     */
    public static boolean isSensitiveField(boolean passwordFlag,
                                            CharSequence className,int inputType) {
        if(passwordFlag)return true;
        int kind=inputType&0x0000000f;
        int variation=inputType&0x00000ff0;
        boolean textPassword=kind==0x01 &&
                (variation==0x80 || variation==0x90 || variation==0xe0);
        boolean numberPassword=kind==0x02 && variation==0x10;
        String clazz=normalize(className);
        return textPassword || numberPassword ||
                clazz.contains("password") || clazz.contains("pinentry");
    }

    /**
     * A matching child label can be wrapped in a clickable ancestor.
     * Never click a dangerous parent, editable container or password field,
     * even when the matching child itself looks harmless.
     */
    public static boolean safeClickableContainer(CharSequence className,
                                                   CharSequence text,
                                                   CharSequence description) {
        String clazz=normalize(className);
        if(clazz.contains("edittext") || clazz.contains("password") ||
                clazz.contains("webview") || clazz.contains("autocompletetextview"))
            return false;
        String visible=normalize(text);
        String announced=normalize(description);
        if(!visible.isEmpty() && !allowedLabel(visible))return false;
        if(!announced.isEmpty() && !allowedLabel(announced))return false;
        return true;
    }

    public static boolean allowedPackage(String candidate, String self) {
        if (candidate == null || candidate.length() < 3 ||
                candidate.length() > 150 || candidate.equals(self)) return false;
        String pkg = candidate.toLowerCase(Locale.ROOT);
        if (pkg.startsWith("com.android.") || pkg.startsWith("android.") ||
                pkg.startsWith("com.google.android.permissioncontroller") ||
                pkg.startsWith("com.google.android.packageinstaller") ||
                pkg.startsWith("com.samsung.android.settings") ||
                pkg.startsWith("com.samsung.android.permissioncontroller") ||
                pkg.startsWith("com.samsung.android.app.telephonyui") ||
                pkg.startsWith("com.google.android.apps.wallet") ||
                pkg.contains("bank") || pkg.contains("wallet") ||
                pkg.contains("payment") || pkg.contains("authenticator") ||
                pkg.contains("security") || pkg.contains("finance") ||
                pkg.contains("crypto") || pkg.contains("binance"))
            return false;
        return pkg.matches("[a-z][a-z0-9_.]{2,149}");
    }
}
