package ai.wolf.autopilot;

import java.util.Locale;

/** Pure JDK safe-conduct rules for external LLM calls. */
public final class CloudBrainPolicy {
    private CloudBrainPolicy() {}

    public static String model(String raw) {
        String m=raw==null?"":raw.trim();
        if(m.length()<4||m.length()>100||
                !m.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)?"))
            throw new IllegalArgumentException("Модель: организация/название[:провайдер].");
        String[] segments = m.split("/", 2);
        if (m.contains("..") || segments[0].startsWith(".") ||
                segments[1].startsWith(".") || segments[0].startsWith("-"))
            throw new IllegalArgumentException("Недопустимые сегменты имени модели.");
        return m;
    }

    public static String question(String raw) {
        String s=raw==null?"":raw.trim();
        if(s.length()<3||s.length()>2200)
            throw new IllegalArgumentException("Запрос облачной модели: 3–2200 символов.");
        String low=s.toLowerCase(Locale.ROOT);
        if(low.matches("(?s).*(?:hf_[a-z0-9]{16,}|gh[pousr]_[a-z0-9]{20,}|sk-[a-z0-9]{20,}).*") ||
           low.matches("(?s).*(?:password|пароль|api[_ -]?key|access[_ -]?token|secret)[=:\\s]+\\S{8,}.*"))
            throw new IllegalArgumentException("Похоже, запрос содержит секрет. Не отправляю его.");
        return s;
    }
}
