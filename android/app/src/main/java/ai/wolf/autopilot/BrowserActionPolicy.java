package ai.wolf.autopilot;

import java.net.URI;
import java.util.Locale;

/**
 * Deterministic, non-credential DOM navigation. Does not expose a model
 * driven JavaScript runner or inspect passwords/forms/cookies/localStorage.
 */
public final class BrowserActionPolicy {
    private BrowserActionPolicy() {}

    public static String safeOrigin(String current) {
        if (current == null || current.length() > 1000)
            throw new IllegalArgumentException("Нет допустимого HTTPS URL.");
        try {
            URI uri = new URI(current);
            if (!"https".equalsIgnoreCase(uri.getScheme()) ||
                    uri.getHost() == null || uri.getRawUserInfo() != null ||
                    uri.getPort() != -1)
                throw new IllegalArgumentException("Доступен только обычный HTTPS.");
            String hostname = uri.getHost().toLowerCase(Locale.ROOT);
            if (hostname.equals("localhost") || hostname.endsWith(".local") ||
                    hostname.contains("bank") || hostname.contains("wallet") ||
                    hostname.contains("payment") || hostname.contains("checkout") ||
                    hostname.contains("crypto") || hostname.contains("binance") ||
                    hostname.contains("login") || hostname.contains("auth.") ||
                    hostname.contains("accounts.google.") ||
                    hostname.contains("paypal") || hostname.contains("stripe") ||
                    hostname.contains("openai.com"))
                throw new IllegalArgumentException("Автоматизация на этом сайте запрещена.");
            return "https://" + hostname;
        } catch (Exception error) {
            throw new IllegalArgumentException("Недопустимый сайт для автоматизации.");
        }
    }

    public static boolean allowedLabel(String label) {
        return AssistivePolicy.allowedLabel(label);
    }

    private static String jsQuote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i=0; i<value.length(); i++) {
            char ch=value.charAt(i);
            switch(ch) {
                case '\\': out.append("\\\\"); break;
                case '"': out.append("\\\""); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '<': out.append("\\u003c"); break;
                case '>': out.append("\\u003e"); break;
                case '&': out.append("\\u0026"); break;
                default:
                    if (ch < 0x20 || ch == 0x2028 || ch == 0x2029)
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int)ch));
                    else out.append(ch);
            }
        }
        return out.append('"').toString();
    }

    /**
     * Fixed JS: only exact visible navigation buttons/links on one HTTPS
     * origin. Never reads inputs/passwords, cookies, private DOM text or
     * executes user-supplied scripts. Preview must be performed before click.
     */
    public static String script(String expectedOrigin, String label, boolean click) {
        if (!allowedLabel(label)) throw new IllegalArgumentException(
                "Название кнопки слишком короткое или относится к опасному действию.");
        String origin = safeOrigin(expectedOrigin);
        String action = click ? "true" : "false";
        return "(function(){" +
                "if(location.origin!==" + jsQuote(origin) + ")return 'origin-changed';" +
                "var want=" + jsQuote(label.trim().replaceAll("\\s+"," ")
                        .toLowerCase(Locale.ROOT)) + ";" +
                "var all=document.querySelectorAll('button,a,[role=button]');" +
                "var matches=[];" +
                "for(var i=0;i<all.length&&i<800;i++){" +
                "var el=all[i];" +
                "if(el.closest('form')||el.matches('[type=submit]'))continue;" +
                "if(el.matches('a[href]')){" +
                "try{if(new URL(el.href,location.href).origin!==location.origin)continue;}" +
                "catch(e){continue;}}" +
                "var rect=el.getBoundingClientRect();" +
                "if(!rect.width||!rect.height||getComputedStyle(el).visibility==='hidden')continue;" +
                "var name=(el.getAttribute('aria-label')||el.innerText||'').trim()" +
                ".replace(/\\s+/g,' ').toLowerCase();" +
                "if(name===want)matches.push(el);" +
                "}" +
                "if(matches.length!==1)return 'matches:'+matches.length;" +
                "if(!" + action + ")return 'ready:1';" +
                "matches[0].click();" +
                "return 'click-invoked';" +
                "})()";
    }
}
