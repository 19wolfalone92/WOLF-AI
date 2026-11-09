package ai.wolf.autopilot;

import java.util.Locale;

/**
 * Explicitly approved, same-origin form DRAFT helper. It NEVER submits.
 * Input events might nonetheless trigger site-specific auto-save. User must
 * review the entire operation and the target site beforehand.
 */
public final class AuthenticatedFormPolicy {
    private AuthenticatedFormPolicy() {}

    private static String jsString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i=0;i<value.length();i++) {
            char c=value.charAt(i);
            if(c=='\\') out.append("\\\\");
            else if(c=='"') out.append("\\\"");
            else if(c=='\n') out.append("\\n");
            else if(c=='\r') out.append("\\r");
            else if(c=='<') out.append("\\u003c");
            else if(c=='>') out.append("\\u003e");
            else if(c=='&') out.append("\\u0026");
            else if(c<0x20 || c=='\u2028' || c=='\u2029')
                out.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    public static String field(String input) {
        String s=input==null?"":input.trim();
        if(s.length()<3||s.length()>80)
            throw new IllegalArgumentException("Название поля — 3–80 символов.");
        String q=s.toLowerCase(Locale.ROOT);
        for(String forbidden:new String[]{
                "password","пароль","otp","token","пин","cvv","card number",
                "credit card","payment","оплата","банковская карта","2fa",
                "secret","пароль","session","ключ api"}) {
            if(q.contains(forbidden)) throw new IllegalArgumentException("Чувствительное поле.");
        }
        return s;
    }

    public static String value(String input) {
        if(input==null||input.length()<1||input.length()>500)
            throw new IllegalArgumentException("Текст — 1–500 символов.");
        if(input.matches("(?is).*(?:hf_[a-z0-9]{16,}|gh[pousr]_[a-z0-9]{20,}|sk-[a-z0-9]{20,}).*"))
            throw new IllegalArgumentException("Возможный секрет. Действие заблокировано.");
        return input;
    }

    /** Only one matching visible plain text/search input or textarea. */
    public static String script(String pageUrl, String rawField,
                                String rawValue, boolean execute) {
        String origin=BrowserActionPolicy.safeOrigin(pageUrl);
        String label=field(rawField).toLowerCase(Locale.ROOT);
        String value=value(rawValue);
        return "(function(){" +
                "if(location.origin!==" + jsString(origin) +
                    ")return 'origin-changed';" +
                "const want=" + jsString(label) + ";" +
                "const items=document.querySelectorAll('input[type=text],input[type=search],textarea');" +
                "const matches=[];" +
                "for(let i=0;i<items.length&&i<180;i++){" +
                    "const el=items[i];" +
                    "if(el.disabled||el.readOnly||el.type==='password')continue;" +
                    "const ac=(el.getAttribute('autocomplete')||'').toLowerCase();" +
                    "if(/password|cc-|one-time-code/.test(ac))continue;" +
                    "const rect=el.getBoundingClientRect();" +
                    "if(rect.width<2||rect.height<2||getComputedStyle(el).visibility==='hidden')continue;" +
                    "const names=[el.getAttribute('aria-label')||'',el.getAttribute('placeholder')||''];" +
                    "if(el.labels)for(let j=0;j<el.labels.length;j++)" +
                        "names.push(el.labels[j].innerText||'');" +
                    "if(names.some(s=>s.trim().replace(/\\s+/g,' ').toLowerCase()===want))matches.push(el);" +
                "}" +
                "if(matches.length!==1)return 'matches:'+matches.length;" +
                (execute ? "matches[0].value=" + jsString(value) + ";" +
                        "matches[0].dispatchEvent(new Event('input',{bubbles:true}));" +
                        "matches[0].dispatchEvent(new Event('change',{bubbles:true}));" +
                        "return 'field-filled-not-submitted';" :
                        "return 'ready:1';") +
                "})()";
    }
}
