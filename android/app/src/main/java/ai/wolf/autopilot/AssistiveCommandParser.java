package ai.wolf.autopilot;

import java.util.Locale;

/** Explicit spoken/text commands only; never infer action from model output. */
public final class AssistiveCommandParser {
    public static final class Plan {
        public final String type;
        public final String label;
        Plan(String type, String label) { this.type=type; this.label=label; }
    }
    private AssistiveCommandParser() {}

    public static Plan parse(String raw) {
        if (raw == null || raw.trim().isEmpty())
            throw new IllegalArgumentException("Не указана команда.");
        String text=raw.trim();
        String lower=text.toLowerCase(Locale.ROOT);
        if (lower.equals("управление приложениями") ||
                lower.equals("включи управление приложениями"))
            return new Plan("enable","");
        if (lower.equals("статус управления") || lower.equals("проверить управление"))
            return new Plan("status","");
        if (lower.equals("отмени управление") || lower.equals("останови управление"))
            return new Plan("cancel","");
        if (lower.equals("пролистай приложение") ||
                lower.equals("прокрути приложение") ||
                lower.equals("пролистай вниз в приложении"))
            return new Plan("scroll","");
        for(String prefix:new String[]{"нажми кнопку ","нажми в приложении "}) {
            if(lower.startsWith(prefix)) {
                String label=text.substring(prefix.length()).trim();
                label=label.replaceAll("^[«\"]|[»\"]$","");
                if (!AssistivePolicy.allowedLabel(label))
                    throw new IllegalArgumentException(
                            "Кнопка заблокирована: укажи безопасный пункт навигации.");
                return new Plan("click",label);
            }
        }
        throw new IllegalArgumentException(
                "Команды: «Управление приложениями», «Нажми кнопку Настройки», " +
                "«Пролистай приложение», «Статус управления», «Отмени управление».");
    }
}
