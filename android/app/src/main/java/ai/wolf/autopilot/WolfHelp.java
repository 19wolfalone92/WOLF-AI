package ai.wolf.autopilot;

import java.util.Locale;

/**
 * Guaranteed, immediate answers to "what can you do?" and short recovery
 * questions. No GGUF unpack, internet, token, or Accessibility permission.
 */
public final class WolfHelp {
    private WolfHelp() {}

    public static String capabilities() {
        return "🐺 Я WOLF AI — помощник на Android. Вот что уже могу:\n\n" +
                "🧠 Отвечать на вопросы локальной Qwen, без обязательной подписки. " +
                "Более мощные Qwen3 1.7B/4B можно установить бесплатно.\n" +
                "🔍 Настраивать глубину: 1–4 локальных прохода. Команда: «Настрой глубину».\n" +
                "🌐 Искать в интернете, читать доступные страницы и сравнивать источники.\n" +
                "🎙️ Слушать голосовые вопросы и озвучивать ответы через голосовой " +
                "движок Android — если он установлен.\n" +
                "📱 Открывать приложения и настройки телефона; выполнять " +
                "ограниченные подтверждённые действия.\n" +
                "📚 Искать информацию в твоих добавленных документах.\n" +
                "💻 Запускать разрешённые проверки и сборки проектов через GitHub.\n" +
                "🎬 Подготавливать MP4 и открывать TikTok Studio напрямую, без Metricool. " +
                "Финальную публикацию на телефоне подтверждаешь ты.\n\n" +
                "Попробуй: «Покажи батарею», «Найди в интернете новости», " +
                "«Настрой глубину», «Тикток студио».\n\n" +
                "🔈 Чтобы проверить голос, нажми «Ответ вслух: выкл». " +
                "Для разговора нажми «🎧 Диалог голосом».\n" +
                "⚠️ Я пока не равен ChatGPT Work: локальная модель может быть " +
                "медленной и ошибаться. Внешние действия требуют разрешений.";
    }

    public static String promptForEmpty() {
        return "🐺 Я на связи. Напиши хотя бы пару слов — например, " +
                "«Что ты можешь?», «Привет» или «Помоги с видео».\n" +
                "Если я долго отвечаю на сложный вопрос, возможно, " +
                "Android ещё загружает локальную нейросеть.";
    }

    public static String recover(String previous) {
        if (previous == null || previous.trim().isEmpty())
            return "Я здесь. Не вижу предыдущего вопроса, который можно " +
                    "обработать повторно. Напиши его ещё раз или спроси " +
                    "«Что ты можешь?» — на это отвечу без загрузки нейросети.";
        String sanitized=previous.replace("\n"," ").trim();
        if (sanitized.length()>130) sanitized=sanitized.substring(0,130)+"…";
        return "Вижу предыдущий вопрос: «"+sanitized+"». " +
                "Похоже, ответ не дошёл или формировался слишком долго. " +
                "Попробую ответить ещё раз. Если локальная модель выдаст ошибку, " +
                "покажу её явно.";
    }

    public static boolean isCapabilityQuestion(String raw) {
        if(raw==null)return false;
        String q=raw.toLowerCase(Locale.ROOT).replaceAll("[!?.,]+"," ")
                .trim().replaceAll("\\s+"," ");
        return q.equals("что ты можешь") || q.equals("что ты умеешь") ||
                q.equals("что ты можешь делать") ||
                q.equals("какие у тебя возможности") ||
                q.equals("на что ты способен") ||
                q.equals("расскажи о своих возможностях") ||
                q.equals("расскажи о себе") ||
                q.equals("возможности wolf") ||
                q.equals("возможности wolf ai");
    }
}
