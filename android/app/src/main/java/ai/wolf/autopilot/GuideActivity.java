package ai.wolf.autopilot;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** User-facing Russian guide, not developer documentation in English. */
public final class GuideActivity extends Activity {
    private final int bg = Color.rgb(10, 15, 28);
    private final int panel = Color.rgb(23, 36, 56);
    private final int white = Color.rgb(241, 247, 255);
    private final int muted = Color.rgb(163, 185, 205);
    private final int cyan = Color.rgb(110, 218, 244);

    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density*n+.5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(size);
        v.setLineSpacing(dp(4), 1.06f);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }
    private void card(LinearLayout parent, String title, String description) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(17), dp(18), dp(17));
        GradientDrawable border = new GradientDrawable();
        border.setColor(panel);
        border.setCornerRadius(dp(20));
        box.setBackground(border);
        LinearLayout.LayoutParams card = new LinearLayout.LayoutParams(-1,-2);
        card.setMargins(0, dp(5), 0, dp(12));
        parent.addView(box, card);
        box.addView(text(title, 19, cyan, true));
        TextView info = text(description, 15, white, false);
        LinearLayout.LayoutParams body = new LinearLayout.LayoutParams(-1,-2);
        body.topMargin = dp(10);
        box.addView(info, body);
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16),dp(19),dp(16),dp(20));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = text("🐺 Как работает WOLF AI — инструкция", 26, white, true);
        root.addView(title);
        TextView introduction = text("У тебя один главный экран — обычный чат. " +
                "Не нужно искать кнопки: напиши или продиктуй просьбу. " +
                "WOLF определит поддерживаемый тип действия и, когда необходимо, " +
                "попросит подтверждение. Все примеры ниже — настоящие команды.", 16, muted, false);
        LinearLayout.LayoutParams paragraph = new LinearLayout.LayoutParams(-1,-2);
        paragraph.setMargins(0,dp(12),0,dp(14));
        root.addView(introduction,paragraph);

        card(root,"💬 1. Общение с ИИ",
                "Пиши любой вопрос, как в ChatGPT: «Объясни закон Ома», " +
                "«Помоги придумать сценарий», «Что означает эта ошибка?». " +
                "Ответ создаёт установленная на телефоне модель Qwen. " +
                "Для сложного анализа можно отдельно импортировать более мощную GGUF " +
                "в меню «Дополнительные инструменты». Маленькая модель " +
                "не равна GPT-6 и может ошибаться.");

        card(root,"🌐 2. Интернет и браузер",
                "Напиши «Найди в интернете лучшие бесплатные инструменты для видео» " +
                "— откроется настоящий поиск в браузере. " +
                "«Открой https://github.com» откроет конкретный сайт. " +
                "«Прочитай сайт https://example.com» с подтверждением " +
                "прочитает доступный текст одной публичной HTTPS-страницы " +
                "и попросит локальную Qwen его объяснить.\n\n" +
                "Сайт может требовать JavaScript и вход в аккаунт: " +
                "браузер работает под твоим управлением. " +
                "Это НЕ облачный компьютер и НЕ автоматический сбор " +
                "всех страниц интернета.");

        card(root,"🎙 3. Разговор голосом",
                "Нажми «🎙 Говорить» под чатом и произнеси команду. " +
                "Когда распознавание завершится, WOLF обработает её " +
                "как обычное текстовое сообщение. " +
                "Нажми «🔈 Ответ вслух: выкл», чтобы включить " +
                "чтение ответов голосом через установленный синтезатор Android.\n\n" +
                "Важно: некоторые движки распознавания и озвучивания " +
                "передают звук через сеть. Для максимальной приватности " +
                "выбирай офлайн-голоса в настройках Android.");

        card(root,"📱 4. Управление телефоном",
                "Примеры: «Покажи батарею», «Открой настройки», " +
                "«Открой Wi-Fi», «Открой камеру», «Открой файлы», " +
                "«Открой календарь», «Открой карты», «Позвони +375…». " +
                "WOLF открывает соответствующее системное окно только " +
                "после твоего подтверждения. Набранный номер сам не вызывает. " +
                "Файлы становятся доступны только после выбора.\n\n" +
                "WOLF не получает root, системные привилегии, доступ " +
                "к чужим сообщениям, скрытое чтение экрана и " +
                "безусловное управление другими приложениями. " +
                "Android специально ограничивает такие возможности.");

        card(root,"📚 5. Документы и долговременные знания",
                "Нажми «＋» возле поля чата и выбери PDF, DOCX, TXT, " +
                "Markdown или подходящий файл кода. " +
                "После твоего согласия WOLF извлечёт доступный текст " +
                "и проиндексирует его на телефоне. " +
                "Затем можно просто спрашивать: «Что написано в моём документе?».\n\n" +
                "Библиотеку и зашифрованные резервные копии " +
                "можно управлять через «Дополнительные инструменты». " +
                "Обучение по файлам здесь означает поисковую память, " +
                "а не автоматическое изменение весов модели.");

        card(root,"💻 6. GitHub и проекты",
                "Напиши «Проверь код WOLF AI» или " +
                "«Запусти тесты проекта Shaira». WOLF определит проект " +
                "и покажет точное задание перед отправкой в GitHub Actions. " +
                "Для этого нужно один раз настроить GitHub PAT через " +
                "«☰ → Дополнительные инструменты → Доступ к GitHub».\n\n" +
                "Результат должен подтверждаться реальными GitHub Actions, " +
                "а не только словами нейросети. Агент пока выполняет " +
                "ограниченные проверки и диагностику — не произвольные " +
                "программные изменения и не автоматическое объединение PR.");

        card(root,"🎬 7. Видеостудия и социальные сети",
                "Существующие функции Metricool и видеостудии " +
                "находятся в «Дополнительные инструменты». " +
                "Публикация требует реальной авторизации и проверки " +
                "подтверждения сервиса. Команда в чате сама по себе " +
                "не означает, что видео уже опубликовано.");

        card(root,"🛡 8. Приватность и безопасность",
                "Обычное общение с локальной Qwen работает без облачного ИИ. " +
                "Интернет-поиск передаёт текст запроса поисковику; " +
                "прочтение сайта требует отдельного согласия. " +
                "Команды GitHub отправляют текст задачи в GitHub Actions. " +
                "Не пиши пароли и токены в чат.\n\n" +
                "WOLF специально не запрашивает доступ ко всем файлам, " +
                "контактам или микрофону в фоне. Ни одно внешнее действие " +
                "не должно выполняться скрытно.");

        card(root,"⚙ 9. Зачем осталось меню инструментов?",
                "Главный чат — для обычных просьб. " +
                "Меню «Дополнительные инструменты» — для редких " +
                "технических задач: настройки моделей, резервные копии, " +
                "GitHub-токен, детальные журналы и Metricool. " +
                "Обычному пользователю туда заходить не требуется.");

        Button back = new Button(this);
        back.setText("← Вернуться в чат");
        back.setAllCaps(false);
        back.setTextColor(bg);
        back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(cyan));
        root.addView(back, new LinearLayout.LayoutParams(-1,dp(58)));
        back.setOnClickListener(v -> finish());
    }
}
