package ai.wolf.autopilot;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain Java DLP for persistent chat (not a claim to detect all PII). */
public final class SensitiveText {
    private SensitiveText() {}
    private static final String HIDDEN="[СЕКРЕТ СКРЫТ]";
    private static final Pattern TOKEN=Pattern.compile(
            "(?i)\\b(?:github_pat_[A-Za-z0-9_]{12,}|gh[pousr]_[A-Za-z0-9_]{16,}|" +
            "hf_[A-Za-z0-9]{12,}|sk-[A-Za-z0-9_-]{12,}|AKIA[A-Z0-9]{16})\\b");
    private static final Pattern BEARER=Pattern.compile(
            "(?i)(\\bBearer\\s+)([A-Za-z0-9._~+/-]{8,})");
    private static final Pattern ASSIGNMENT=Pattern.compile(
            "(?i)([\\\"']?\\b(?:access_token|refresh_token|password|passwd|" +
            "api[_-]?key|secret|authorization)\\b[\\\"']?\\s*[:=]\\s*[\\\"']?)" +
            "([^\\s\\\"',}]{4,})");

    /** Redact before SQLite writes, reading legacy chat, or .wolfpack export. */
    public static String forStorage(String text) {
        if(text==null)return "";
        String hidden=TOKEN.matcher(text).replaceAll(HIDDEN);
        hidden=BEARER.matcher(hidden).replaceAll("$1"+HIDDEN);
        Matcher assignment=ASSIGNMENT.matcher(hidden);
        StringBuffer output=new StringBuffer();
        while(assignment.find()){
            assignment.appendReplacement(output,Matcher.quoteReplacement(
                    assignment.group(1)+HIDDEN));
        }
        assignment.appendTail(output);
        return output.toString();
    }

    /** True for unmistakable credential syntax; false positives intentionally minimal. */
    public static boolean containsCredential(String text) {
        if(text==null)return false;
        return !forStorage(text).equals(text);
    }
}
