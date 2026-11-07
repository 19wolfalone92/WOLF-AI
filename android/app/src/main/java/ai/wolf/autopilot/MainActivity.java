package ai.wolf.autopilot;

import android.app.Activity;
import android.content.Intent;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * No paid API. Native Android front-end for free-first WOLF checks and local
 * notes; llama.cpp chat is optional and localhost-only.
 */
public final class MainActivity extends Activity {
    private static final int EXPORT_LEARNING = 6140;
    private static final int IMPORT_LEARNING = 6141;
    private static final int BG = Color.rgb(8, 14, 27);
    private static final int PANEL = Color.rgb(19, 30, 50);
    private static final int INPUT = Color.rgb(29, 42, 64);
    private static final int WHITE = Color.rgb(237, 245, 255);
    private static final int MUTED = Color.rgb(165, 181, 204);
    private static final int CYAN = Color.rgb(95, 211, 255);
    private static final int GREEN = Color.rgb(122, 231, 170);
    private static final int ORANGE = Color.rgb(255, 196, 114);
    private static final String[] PROJECT_CODES = {
            "wolf", "studio", "shaira", "williams", "williams-ai", "english-child"
    };
    private static final String[] PROJECT_TITLES = {
            "🐺 WOLF AI — самоаудит", "🎬 STORY GLITCH Studio", "❤️ Shaira — русский язык",
            "📈 Williams Bot", "🧠 Williams AI", "📚 Eva English Child"
    };
    private static final String[] MODES = {"audit", "test"};
    private static final String[] MODE_TITLES = {"Аудит", "Тестирование"};

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private SecureStore secrets;
    private TaskStore tasks;
    private ChatStore chat;
    private LearningStore learning;
    private MetricoolOAuth metricoolAuth;
    private MetricoolMcp metricoolMcp;
    private TextView metricoolStatus;
    private EditText metricoolBrand;
    private EditText metricoolVideo;
    private EditText metricoolText;
    private EditText metricoolWhen;
    private Spinner metricoolNetwork;
    private android.widget.CheckBox metricoolAuto;
    private TrainedRouter trainedRouter;
    private PublicationCenter.VideoInfo selectedVideo;
    private Spinner publicationSpinner;
    private EditText publicationCaption;
    private EditText learningFact;
    private EditText learningExample;
    private TextView publicationStatus;
    private TextView learningStatus;
    private EditText filePathInput;
    private EditText codeEditor;
    private TextView codeStatus;
    private GithubCode.FileData loadedFile;
    private Spinner projectSpinner;
    private Spinner modeSpinner;
    private EditText commandInput;
    private EditText tokenInput;
    private EditText questionInput;
    private TextView connectionState;
    private TextView resultBox;
    private TextView aiOutput;
    private TextView aiStatus;
    private TextView cloudStatus;
    private TextView backlogStatus;
    private LinearLayout tasksContainer;
    private LinearLayout runsContainer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        secrets = new SecureStore(this);
        tasks = new TaskStore(this);
        chat = new ChatStore(this);
        learning = new LearningStore(this);
        metricoolAuth = new MetricoolOAuth(this);
        metricoolMcp = new MetricoolMcp(this, metricoolAuth);
        try { trainedRouter = TrainedRouter.load(getApplicationContext()); }
        catch (Exception ignored) { trainedRouter = null; }
        buildUi();
        updateTokenStatus();
        renderTasks();
        handleMetricoolCallback(getIntent());
        // Public read is possible without a token. No auto-execution on app start.
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private GradientDrawable shape(int fill, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private void pad(View v, int x, int y) {
        v.setPadding(dp(x), dp(y), dp(x), dp(y));
    }

    private LinearLayout card(LinearLayout parent, String title, String subtitle) {
        LinearLayout section = vertical();
        section.setBackground(shape(PANEL, 17));
        pad(section, 17, 16);
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        outer.setMargins(0, 0, 0, dp(15));
        parent.addView(section, outer);
        section.addView(text(title, 19, WHITE, true));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView desc = text(subtitle, 13, MUTED, false);
            add(section, desc, 0, 7);
        }
        return section;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setLineSpacing(dp(2), 1);
        if (bold) v.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return v;
    }

    private void add(LinearLayout parent, View child, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(top), 0, dp(bottom));
        parent.addView(child, params);
    }

    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(primary ? BG : CYAN);
        b.setBackground(shape(primary ? CYAN : INPUT, 12));
        b.setMinHeight(dp(48));
        pad(b, 12, 10);
        return b;
    }

    private EditText entry(String hint, boolean multiline) {
        EditText e = new EditText(this);
        e.setTextColor(WHITE);
        e.setHintTextColor(MUTED);
        e.setHint(hint);
        e.setTextSize(15);
        e.setBackground(shape(INPUT, 12));
        e.setInputType(InputType.TYPE_CLASS_TEXT |
                (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        e.setSingleLine(!multiline);
        if (multiline) {
            e.setMinLines(2);
            e.setMaxLines(5);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        pad(e, 13, 13);
        return e;
    }

    private Spinner spinner(String[] labels) {
        Spinner s = new Spinner(this);
        ArrayAdapter<String> a = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, labels) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(WHITE);
                t.setTextSize(16);
                return t;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(position, convertView, parent);
                t.setTextColor(WHITE);
                t.setBackgroundColor(INPUT);
                pad(t, 15, 13);
                return t;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(a);
        s.setBackground(shape(INPUT, 11));
        s.setMinimumHeight(dp(46));
        return s;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout page = vertical();
        pad(page, 16, 22);
        scroll.addView(page);
        setContentView(scroll);

        TextView brand = text("🐺 WOLF AI", 32, CYAN, true);
        add(page, brand, 0, 2);
        add(page, text("Автономный центр управления • Android v2.0", 14, MUTED, false), 0, 18);

        LinearLayout intro = card(page, "⚡ Бесплатный базовый режим",
                "Задачи хранятся на телефоне. GitHub выполняет разрешённые проверки. " +
                "Локальный ИИ необязателен. Work и платные API не нужны.");
        TextView banner = text("● OFFLINE-FIRST  •  НЕТ РЕКЛАМЫ  •  НЕТ АВТОПУБЛИКАЦИИ", 12, GREEN, true);
        add(intro, banner, 11, 0);

        LinearLayout commands = card(page, "📝 Новое задание",
                "Напиши обычным текстом. Сейчас команды классифицируются локально, " +
                "но не выполняют произвольные действия и не исправляют код автоматически.");
        commandInput = entry("Например: Проверь ошибки приложения Shaira", true);
        add(commands, commandInput, 12, 10);
        Button create = button("Добавить в локальные задачи", true);
        add(commands, create, 0, 4);
        create.setOnClickListener(v -> {
            try {
                String body = commandInput.getText().toString().trim();
                CommandParser.Route route = smartRoute(body);
                tasks.add(body, route.project, route.mode);
                commandInput.setText("");
                showResult("Задача сохранена на телефоне. Для запуска проверки нажми «Проверить» возле задачи.");
                renderTasks();
            } catch (Exception e) {
                showResult(e.getMessage());
            }
        });
        Button createAndRun = button("💥 Сохранить и запустить проверку", false);
        add(commands, createAndRun, 4, 8);
        createAndRun.setOnClickListener(v -> {
            try {
                String body = commandInput.getText().toString().trim();
                CommandParser.Route route = smartRoute(body);
                long id = tasks.add(body, route.project, route.mode);
                commandInput.setText("");
                renderTasks();
                for (TaskStore.Task task : tasks.recent()) {
                    if (task.id == id) {
                        dispatchTask(task);
                        break;
                    }
                }
            } catch (Exception e) {
                showResult("Не удалось сохранить задачу: " + e.getMessage());
            }
        });
        tasksContainer = vertical();
        add(commands, tasksContainer, 12, 0);

        LinearLayout dispatch = card(page, "⚙️ Запуск GitHub Actions",
                "Запускается существующий WOLF AI audit workflow. " +
                "Ни один проект не изменяется, сделки и публикации запрещены.");
        add(dispatch, text("Проект", 13, MUTED, true), 11, 5);
        projectSpinner = spinner(PROJECT_TITLES);
        add(dispatch, projectSpinner, 0, 7);
        add(dispatch, text("Режим", 13, MUTED, true), 3, 5);
        modeSpinner = spinner(MODE_TITLES);
        add(dispatch, modeSpinner, 0, 11);
        Button run = button("▶ Запустить проверку", true);
        add(dispatch, run, 0, 7);
        run.setOnClickListener(v -> dispatchSelected(selectedProject(), selectedMode()));
        Button refresh = button("↻ Обновить запуски", false);
        add(dispatch, refresh, 0, 9);
        refresh.setOnClickListener(v -> updateRuns());
        resultBox = text("Здесь появится результат запуска.", 13, ORANGE, false);
        add(dispatch, resultBox, 0, 12);
        runsContainer = vertical();
        add(dispatch, runsContainer, 0, 0);


        LinearLayout cloud = card(page, "☁️ Бесплатные интеграции GitHub",
                "Сборка Android APK, технический предпросмотр Studio и проверка бесплатных провайдеров.");
        cloudStatus = text("Статус GitHub ещё не проверен.", 13, MUTED, false);
        add(cloud, cloudStatus, 9, 10);

        Button repair = button("🛠 Самоисправление WOLF AI → Pull Request", true);
        add(cloud, repair, 0, 8);
        repair.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Проверяемое самоисправление WOLF AI")
                .setMessage("GitHub выполнит только безопасные автоматические исправления Python " +
                        "в WOLF-AI, затем прогонит тесты и, если есть изменения, попробует " +
                        "создать отдельный Pull Request. В main ничего автоматически не попадёт. " +
                        "Другие проекты, торговля и TikTok не затрагиваются. Запустить?")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Запустить", (dialog, button) ->
                        runTrustedWorkflow(GithubOperations.REPAIR))
                .show());
        Button buildApk = button("📲 Собрать APK WOLF AI", true);
        add(cloud, buildApk, 0, 7);
        buildApk.setOnClickListener(v -> runTrustedWorkflow(GithubOperations.APK));
        Button studioPreview = button("🎬 Проверить видеостудию (без публикации)", false);
        add(cloud, studioPreview, 0, 7);
        studioPreview.setOnClickListener(v -> runTrustedWorkflow(GithubOperations.STUDIO));
        Button providerTests = button("🧪 Тестировать бесплатные провайдеры", false);
        add(cloud, providerTests, 0, 7);
        providerTests.setOnClickListener(v -> runTrustedWorkflow(GithubOperations.FREE_TESTS));
        Button train = button("🧪 Обучить маршрутизатор задач на CPU", false);
        add(cloud, train, 0, 7);
        train.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Обучить маршрутизатор задач?")
                .setMessage("В GitHub запустится обучение маленького классификатора " +
                        "по открытым учебным примерам репозитория. " +
                        "Результат — JSON-модель и отчёт о точности. " +
                        "Это НЕ дообучение Qwen и НЕ использование личных заметок.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Запустить", (dialog, button) ->
                        runTrustedWorkflow(GithubOperations.TRAIN))
                .show());
        Button checkCloud = button("↻ Проверить 5 процессов GitHub", false);
        add(cloud, checkCloud, 0, 7);
        checkCloud.setOnClickListener(v -> refreshTrustedWorkflows());
        backlogStatus = text("Очередь задач GitHub и Pull Requests пока не загружена.", 13, MUTED, false);
        add(cloud, backlogStatus, 0, 8);
        Button showBacklog = button("📋 Задания GitHub и открытые PR", false);
        add(cloud, showBacklog, 0, 8);
        showBacklog.setOnClickListener(v -> {
            backlogStatus.setText("Считываю публичные Issues и Pull Requests…");
            async(() -> GithubBacklog.list(false, secrets.load()) + "\n" +
                            GithubBacklog.list(true, secrets.load()),
                    value -> {
                        backlogStatus.setText(value);
                        android.text.util.Linkify.addLinks(backlogStatus,
                                android.text.util.Linkify.WEB_URLS);
                        backlogStatus.setMovementMethod(
                                android.text.method.LinkMovementMethod.getInstance());
                    },
                    error -> backlogStatus.setText("GitHub недоступен: " + error));
        });
        Button aiDiagnose = button("🧠 Объяснить последнюю ошибку CI локальным ИИ", false);
        add(cloud, aiDiagnose, 0, 7);
        aiDiagnose.setOnClickListener(v -> analyzeRecentFailure());
        Button artifacts = button("📦 APK и Artifacts в GitHub", false);
        add(cloud, artifacts, 0, 0);
        artifacts.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/19wolfalone92/WOLF-AI/actions/workflows/wolf-ai-android.yml"))));

        LinearLayout developer = card(page, "💻 GitHub — чтение кода и Pull Request",
                "WOLF AI может прочитать небольшой Python-файл WOLF-AI, " +
                "предложить исправление через локальную Qwen, " +
                "а после твоего подтверждения создать отдельную ветку и Pull Request. " +
                "Никакого автоматического merge и изменений других проектов.");
        codeStatus = text("Готов к чтению WOLF-AI. " +
                "Нужны Contents и Pull requests: Read and write.", 13, MUTED, false);
        add(developer, codeStatus, 9, 9);
        filePathInput = entry("Например: wolf_ai/train_router.py", false);
        add(developer, filePathInput, 0, 8);
        Button readCode = button("📂 Прочитать исходник из GitHub", true);
        add(developer, readCode, 0, 8);
        codeEditor = entry("Код загруженного файла появится здесь…", true);
        codeEditor.setMinLines(7);
        codeEditor.setMaxLines(14);
        codeEditor.setTextSize(12);
        add(developer, codeEditor, 0, 9);
        readCode.setOnClickListener(v -> {
            String path = filePathInput.getText().toString().trim();
            codeStatus.setText("Получаю исходник из GitHub…");
            loadedFile = null;
            async(() -> {
                GithubCode.FileData value = GithubCode.fetch(path, secrets.load());
                loadedFile = value;
                return value.content;
            }, value -> {
                codeEditor.setText(value);
                codeStatus.setText("Загружено: " + path + "\n" +
                        "Изменения не сохраняются в GitHub до создания PR.");
            }, error -> codeStatus.setText("Ошибка чтения GitHub: " + error));
        });
        Button askCode = button("🧠 Получить совет локальной Qwen по коду", false);
        add(developer, askCode, 0, 8);
        askCode.setOnClickListener(v -> {
            if (loadedFile == null) {
                codeStatus.setText("Сначала загрузи исходник."); return;
            }
            String snippet = codeEditor.getText().toString();
            if (snippet.length() > 550) snippet = snippet.substring(0, 550);
            String prompt = "Изучи короткий фрагмент Python и предложи " +
                    "одно безопасное улучшение. Не утверждай, что правка применена. " +
                    "Ответь по-русски:\n" + snippet;
            codeStatus.setText("Qwen анализирует фрагмент локально…");
            async(() -> EmbeddedAi.ask(getApplicationContext(), prompt,
                    java.util.Collections.emptyList()), suggestion ->
                    codeStatus.setText("Предложение локального ИИ (не применено):\n" +
                            suggestion), error -> codeStatus.setText("ИИ недоступен: " + error));
        });
        Button openPr = button("🌿 Создать Pull Request из проверенной правки", false);
        add(developer, openPr, 0, 0);
        openPr.setOnClickListener(v -> {
            if (loadedFile == null) {
                codeStatus.setText("Сначала прочитай исходник."); return;
            }
            String edit = codeEditor.getText().toString();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Создать Pull Request в WOLF-AI?")
                    .setMessage("Только " + loadedFile.path +
                            " будет заменён на текст из редактора " +
                            "в НОВОЙ ветке. Это реальное изменение GitHub, " +
                            "но main не меняется. Нужны Contents и Pull requests: " +
                            "Read and write. Проверь код внимательно. Продолжить?")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Создать PR", (dialog, button) -> {
                        codeStatus.setText("Создаю отдельную ветку и PR…");
                        GithubCode.FileData current = loadedFile;
                        async(() -> GithubCode.propose(current, edit, secrets.load()), url -> {
                            codeStatus.setText("Pull Request создан:\n" + url +
                                    "\nИзменения не объединены и должны пройти тесты.");
                            android.text.util.Linkify.addLinks(codeStatus,
                                    android.text.util.Linkify.WEB_URLS);
                            codeStatus.setMovementMethod(
                                    android.text.method.LinkMovementMethod.getInstance());
                            loadedFile = null; // Prevent accidental duplicate submission.
                        }, error -> codeStatus.setText("PR не создан: " + error +
                                "\nЕсли ветка появилась, проверь GitHub вручную."));
                    }).show();
        });

        LinearLayout publish = card(page, "🎬 Центр публикаций — TikTok / YouTube / Instagram",
                "Выбери готовый MP4 с телефона, проверь его параметры и передай видео " +
                "в установленное приложение соцсети. Финальная публикация и настройки " +
                "доступа подтверждаются там. Автопубликация без одобрения НЕ выполняется.");
        publicationStatus = text("Видео не выбрано. Поддерживается MP4 до 1.5 ГБ.", 13, MUTED, false);
        add(publish, publicationStatus, 11, 9);
        Button selectVideo = button("📁 Выбрать видео MP4", true);
        add(publish, selectVideo, 0, 8);
        selectVideo.setOnClickListener(v -> {
            try { startActivityForResult(PublicationCenter.pickIntent(), PublicationCenter.PICK_VIDEO); }
            catch (Exception e) { publicationStatus.setText("Не удалось открыть файлы: " + e.getMessage()); }
        });
        add(publish, text("Куда передать видео", 13, MUTED, true), 4, 5);
        publicationSpinner = spinner(PublicationCenter.NETWORKS);
        add(publish, publicationSpinner, 0, 8);
        publicationCaption = entry("Подпись, название, #хештеги (до 2000 символов)", true);
        add(publish, publicationCaption, 0, 9);
        Button shareVideo = button("🚀 Открыть публикацию в выбранном сервисе", false);
        add(publish, shareVideo, 0, 9);
        shareVideo.setOnClickListener(v -> shareSelectedVideo());
        Button openPlanner = button("📆 Планировщики: TikTok Studio / YouTube / Metricool", false);
        add(publish, openPlanner, 0, 7);
        openPlanner.setOnClickListener(v -> {
            String caption = publicationCaption.getText().toString().trim();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Открыть планировщик публикаций")
                    .setItems(new String[]{
                            "TikTok Studio (официальная загрузка и расписание)",
                            "YouTube Studio (официальная загрузка и расписание)",
                            "Metricool (подключённые социальные сети)"
                    }, (dialog, index) -> {
                        if (!caption.isEmpty()) {
                            ClipboardManager cm =
                                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) cm.setPrimaryClip(
                                    ClipData.newPlainText("WOLF video caption", caption));
                        }
                        String url = index == 0 ?
                                "https://www.tiktok.com/tiktokstudio/upload" :
                                index == 1 ? "https://studio.youtube.com/" :
                                "https://app.metricool.com/";
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        publicationStatus.setText("Открыт официальный планировщик. " +
                                "Выбери видео и дату внутри сервиса. " +
                                "Подпись скопирована, если была задана. " +
                                "WOLF AI не утверждает, что публикация запланирована.");
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });
        add(publish, text("⚠️ Встроенный доступ к Metricool API не включён: " +
                "бесплатный тариф не предоставляет REST API. " +
                "Встроена передача через системный Android Share; " +
                "она не гарантирует автоматическую публикацию.", 12, ORANGE, false), 5, 0);

        LinearLayout learn = card(page, "📚 Обучение и память WOLF AI",
                "Сохраняй полезные факты и собственные примеры маршрутизации. " +
                "Приложение учитывает их локально, без платного API. " +
                "Веса нейросети Qwen при этом не изменяются.");
        learningStatus = text("Память загружена.", 13, MUTED, false);
        add(learn, learningStatus, 9, 8);
        learningFact = entry("Чему запомнить? Например: сюжет сериала состоит из 30 серий", true);
        add(learn, learningFact, 0, 8);
        Button remember = button("💾 Запомнить этот факт", true);
        add(learn, remember, 0, 10);
        remember.setOnClickListener(v -> {
            try {
                learning.teach(learningFact.getText().toString());
                learningFact.setText("");
                refreshLearningStatus("Факт добавлен в локальную память.");
            } catch (Exception error) { refreshLearningStatus(error.getMessage()); }
        });
        learningExample = entry("Пример задания для выбранного проекта", true);
        add(learn, learningExample, 0, 8);
        Button teachRoute = button("🧭 Научить определять выбранный проект", false);
        add(learn, teachRoute, 0, 9);
        teachRoute.setOnClickListener(v -> {
            try {
                learning.teachRoute(learningExample.getText().toString(), selectedProject());
                learningExample.setText("");
                refreshLearningStatus("Пример классификации сохранён для " + selectedProject() + ".");
            } catch (Exception error) { refreshLearningStatus(error.getMessage()); }
        });
        Button backup = button("💾 Экспорт изученного в JSON", false);
        add(learn, backup, 0, 7);
        backup.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_TITLE, "wolf-learning-backup.json");
            startActivityForResult(intent, EXPORT_LEARNING);
        });
        Button restore = button("📂 Восстановить знания из JSON", false);
        add(learn, restore, 0, 7);
        restore.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            startActivityForResult(intent, IMPORT_LEARNING);
        });
        add(learn, text("Резервная копия не зашифрована. " +
                "Содержит только явно сохранённые факты и примеры, " +
                "но не GitHub-токен и не историю чата. " +
                "Не делись копией с посторонними.", 12, ORANGE, false), 2, 8);
        Button clearKnowledge = button("🗑 Очистить всё изученное на телефоне", false);
        add(learn, clearKnowledge, 0, 0);
        clearKnowledge.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Стереть локальные знания?")
                .setMessage("Все факты и учебные примеры WOLF AI будут удалены с телефона. " +
                        "История чата и задачи останутся.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Очистить", (dialog, button) -> {
                    learning.clear();
                    refreshLearningStatus("Локальные знания очищены.");
                }).show());
        refreshLearningStatus(trainedRouter == null ?
                "Локальное обучение готово (обученная модель не найдена)." :
                "Локальное обучение готово. CPU-маршрутизатор встроен в APK.");

        LinearLayout metricool = card(page, "📡 Metricool MCP — без платного REST API",
                "Официальное OAuth-подключение к Metricool MCP, доступное и на Free. " +
                "После входа можно проверить подключённые бренды и отправлять " +
                "одобренные задания на публикацию в TikTok, YouTube, Instagram, Facebook. " +
                "Месячные лимиты тарифа сохраняются.");
        metricoolStatus = text(metricoolAuth.connected() ?
                "OAuth токен сохранён на телефоне. Проверка ещё не проводилась." :
                "Metricool MCP ещё не авторизован. Требуется разрешение владельца аккаунта.",
                13, ORANGE, false);
        add(metricool, metricoolStatus, 9, 9);
        Button connectMetricool = button("🔐 Подключить Metricool Free через OAuth", true);
        add(metricool, connectMetricool, 0, 8);
        connectMetricool.setOnClickListener(v -> {
            metricoolStatus.setText("Регистрирую публичный OAuth клиент…");
            async(() -> metricoolAuth.beginAuthorization(), intent -> {
                try {
                    metricoolStatus.setText("Открываю страницу входа Metricool…");
                    startActivity(intent);
                } catch (Exception ex) {
                    metricoolStatus.setText("Не удалось открыть OAuth: " + ex.getMessage());
                }
            }, error -> metricoolStatus.setText("Metricool OAuth недоступен: " +
                    error + "\nМожно продолжать через Android Share и официальный планировщик."));
        });
        Button readBrands = button("🔎 Проверить подключение и доступные бренды", false);
        add(metricool, readBrands, 0, 8);
        readBrands.setOnClickListener(v -> {
            metricoolStatus.setText("Выполняю реальный запрос к Metricool MCP…");
            async(() -> metricoolMcp.brands(), result -> {
                metricoolStatus.setText(result);
                metricoolStatus.setMovementMethod(
                        android.text.method.ScrollingMovementMethod.getInstance());
            }, error -> metricoolStatus.setText("MCP запрос не прошёл: " +
                    error + "\nНикаких публикаций не выполнено."));
        });
        add(metricool, text("Настройки публикации — только публичный URL MP4", 14, CYAN, true), 5, 8);
        metricoolBrand = entry("Числовой ID бренда Metricool", false);
        add(metricool, metricoolBrand, 0, 8);
        metricoolVideo = entry("https://public-host.example/video.mp4", false);
        add(metricool, metricoolVideo, 0, 8);
        metricoolText = entry("Название / описание / хештеги (до 1800 символов)", true);
        add(metricool, metricoolText, 0, 8);
        metricoolWhen = entry("Дата с UTC offset: 2026-12-01T19:00:00+03:00", false);
        add(metricool, metricoolWhen, 0, 8);
        metricoolNetwork = spinner(new String[]{"tiktok","youtube","instagram","facebook"});
        add(metricool, metricoolNetwork, 0, 9);
        metricoolAuto = new android.widget.CheckBox(this);
        metricoolAuto.setText("Автоматическая публикация по расписанию (иначе черновик)");
        metricoolAuto.setTextColor(WHITE);
        metricoolAuto.setChecked(false);
        add(metricool, metricoolAuto, 0, 9);
        Button createMetricool = button("📅 Отправить пост в Metricool после проверки", false);
        add(metricool, createMetricool, 0, 9);
        createMetricool.setOnClickListener(v -> scheduleMetricoolPost());
        Button disconnectMetricool = button("Отключить Metricool на этом телефоне", false);
        add(metricool, disconnectMetricool, 0, 0);
        disconnectMetricool.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Отключить Metricool?")
                .setMessage("Локальный OAuth-токен будет удалён. " +
                        "Для полного отзыва доступа проверь также авторизованные приложения " +
                        "в настройках Metricool.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Отключить", (dialog, button) -> {
                    metricoolAuth.disconnect();
                    metricoolStatus.setText("Metricool локально отключён.");
                }).show());
        add(metricool, text("Важно: нужен постоянно доступный HTTPS URL MP4. " +
                "Файлы только на телефоне недоступны облачному планировщику. " +
                "Авторизация OAuth может не работать, если Metricool отклоняет регистрацию " +
                "стороннего Android-клиента. WOLF никогда не обходит отказ сервера.",
                12, MUTED, false), 6, 0);

        LinearLayout credentials = card(page, "🔐 Доступ к GitHub",
                "Для проверок нужны Actions: Read and write на WOLF-AI и Two-Worlds-Studio. " +
                "Для публикации задач нужны Issues: Read and write на WOLF-AI. " +
                "Для редактора кода нужны Contents и Pull requests: Read and write на WOLF-AI. " +
                "Сохраняется зашифрованно через Android Keystore, не загружается в репозиторий.");
        connectionState = text("Статус токена", 13, MUTED, true);
        add(credentials, connectionState, 12, 6);
        tokenInput = entry("Вставь токен github_pat_…", false);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        add(credentials, tokenInput, 3, 8);
        Button save = button("Сохранить токен", true);
        add(credentials, save, 0, 7);
        save.setOnClickListener(v -> {
            try {
                secrets.save(tokenInput.getText().toString());
                tokenInput.setText("");
                updateTokenStatus();
                showResult("Токен сохранён в зашифрованном хранилище Android.");
            } catch (Exception e) {
                showResult("Не удалось сохранить токен: " + e.getMessage());
            }
        });
        Button clear = button("Удалить токен", false);
        add(credentials, clear, 0, 0);
        clear.setOnClickListener(v -> {
            secrets.clear();
            updateTokenStatus();
            showResult("Локальный GitHub-токен удалён.");
        });

        LinearLayout llm = card(page, "🧠 ВСТРОЕННЫЙ ИИ — без интернета",
                "Qwen2.5-Coder 0.5B + llama.cpp находятся внутри APK. " +
                "Не нужен Termux, компьютер, доступ к сервисам или платный API. " +
                "При первом обращении модель распакуется в память приложения (~430 МБ).");
        aiStatus = text("● Модель в комплекте APK • Android ARM64", 13, GREEN, true);
        add(llm, aiStatus, 11, 7);
        Button checkAi = button("⚡ Запустить встроенный ИИ", true);
        add(llm, checkAi, 0, 8);
        checkAi.setOnClickListener(v -> {
            aiStatus.setText("Проверяю SHA256 и загружаю локальную модель…");
            async(() -> EmbeddedAi.check(getApplicationContext()), value -> {
                aiStatus.setTextColor(GREEN);
                aiStatus.setText("● " + value);
            }, error -> {
                aiStatus.setTextColor(ORANGE);
                aiStatus.setText("○ Ошибка модели: " + error);
            });
        });
        questionInput = entry("Вопрос для встроенного ИИ…", true);
        add(llm, questionInput, 13, 8);
        Button ask = button("🧠 Спросить ИИ офлайн", false);
        add(llm, ask, 0, 9);
        aiOutput = text("Модель уже внутри приложения. Для начала просто задай вопрос.", 13, MUTED, false);
        add(llm, aiOutput, 0, 0);
        ask.setOnClickListener(v -> {
            String question = questionInput.getText().toString().trim();
            aiOutput.setText("Думаю на процессоре телефона…");
            async(() -> EmbeddedAi.ask(getApplicationContext(), question, chat.recent(4),
                    learning.related(question, 3)), answer -> {
                chat.add("user", question);
                chat.add("assistant", answer);
                aiOutput.setText(answer);
                questionInput.setText("");
            }, error -> aiOutput.setText("Не удалось запустить встроенный ИИ: " + error));
        });
        Button clearChat = button("Очистить память локального чата", false);
        add(llm, clearChat, 8, 0);
        clearChat.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
                .setTitle("Удалить историю локального ИИ?")
                .setMessage("Сообщения будут удалены только с этого телефона.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Удалить", (dialog, button) -> {
                    chat.clearHistory();
                    aiOutput.setText("Локальная история очищена.");
                })
                .show());

        TextView footer = text("WOLF AI 3.1 • Metricool MCP OAuth • Обучение", 12, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        add(page, footer, 10, 22);
    }

    private CommandParser.Route smartRoute(String body) {
        String taught = learning.learnedProject(body);
        String modelGuess = null;
        if (trainedRouter != null && "wolf".equals(selectedProject())) {
            try { modelGuess = trainedRouter.predict(body); }
            catch (Exception ignored) { modelGuess = null; }
        }
        // Explicitly taught examples > packaged CPU model > the selected fallback.
        String fallback = taught != null ? taught :
                (modelGuess != null ? modelGuess : selectedProject());
        return CommandParser.parse(body, fallback, selectedMode());
    }

    private String selectedProject() {
        return PROJECT_CODES[projectSpinner.getSelectedItemPosition()];
    }

    private String selectedMode() {
        return MODES[modeSpinner.getSelectedItemPosition()];
    }

    private void updateTokenStatus() {
        connectionState.setText(secrets.configured()
                ? "● Токен сохранён на устройстве"
                : "○ Токен не добавлен. Чтение публичных запусков доступно без токена.");
        connectionState.setTextColor(secrets.configured() ? GREEN : ORANGE);
    }

    private interface RemoteAction { String call() throws Exception; }
    private interface OnSuccess { void handle(String value); }
    private interface OnError { void handle(String value); }

    private void async(RemoteAction work, OnSuccess success, OnError failure) {
        executor.execute(() -> {
            try {
                String value = work.call();
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) success.handle(value); });
            } catch (Exception exception) {
                String reason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) failure.handle(reason); });
            }
        });
    }

    private void showResult(String text) {
        if (resultBox != null) resultBox.setText(text == null ? "Ошибка без описания" : text);
    }

    private void dispatchSelected(String project, String mode) {
        showResult("Отправляю запрос на проверку " + project + "…");
        async(() -> GithubApi.dispatch(project, mode, secrets.load()),
                answer -> { showResult(answer); updateRuns(); },
                error -> showResult("Проверка не запущена: " + error));
    }

    private void dispatchTask(TaskStore.Task task) {
        if (!TaskStore.DRAFT.equals(task.status)) {
            showResult("Задание уже отправлено. Обнови статусы вместо повторного запуска.");
            return;
        }
        showResult("Отправляю задачу " + task.uid + "…");
        async(() -> GithubApi.dispatch(task.project, task.mode, task.uid, secrets.load()),
                answer -> {
                    tasks.submitted(task.id, task.uid);
                    renderTasks();
                    showResult(answer);
                    updateRuns();
                },
                error -> showResult("Задание осталось локальным: " + error));
    }

    private void showJobDetails(long runId) {
        showResult("Получаю реальные результаты шагов #" + runId + "…");
        async(() -> GithubApi.jobSummary(runId, secrets.load()),
                this::showResult,
                error -> showResult("Детали GitHub пока недоступны: " + error));
    }


    /** Actual cross-tool integration: read GitHub's failed steps, then ask localhost model. */
    private void analyzeRecentFailure() {
        cloudStatus.setText("Ищу ошибку в реальных GitHub Actions…");
        async(() -> {
            List<GithubApi.RunInfo> list = GithubApi.runs(secrets.load());
            GithubApi.RunInfo selected = null;
            for (GithubApi.RunInfo run : list) {
                if ("completed".equals(run.status) && !"success".equals(run.conclusion)) {
                    selected = run;
                    break;
                }
            }
            if (selected == null)
                return "Нет подтверждённого неуспешного запуска WOLF AI audit в последних 50. " +
                        "Не буду придумывать ошибку.";
            String evidence = GithubApi.jobSummary(selected.id, secrets.load());
            String prompt = "Объясни по-русски простыми словами эту ошибку CI и " +
                    "предложи 1–3 безопасных действия. Не утверждай, что что-либо исправлено:\n" +
                    evidence.substring(0, Math.min(850, evidence.length()));
            try {
                return "Данные GitHub #" + selected.id + ":\n" + evidence +
                        "\n\nАнализ локального ИИ:\n" + EmbeddedAi.ask(getApplicationContext(), prompt, java.util.Collections.emptyList());
            } catch (Exception localError) {
                return "Данные GitHub #" + selected.id + ":\n" + evidence +
                        "\n\nИИ не подключился: " + localError.getMessage() +
                        "\nЗапусти локальную Qwen через кнопку установки ниже.";
            }
        }, value -> cloudStatus.setText(value),
           error -> cloudStatus.setText("Не удалось получить диагноз: " + error));
    }

    private void runTrustedWorkflow(String workflow) {
        cloudStatus.setText("Отправляю в GitHub: " + workflow + "…");
        async(() -> GithubOperations.dispatch(workflow, secrets.load()), result -> {
            cloudStatus.setText(result);
            refreshTrustedWorkflows();
        }, error -> cloudStatus.setText("Запуск не выполнен: " + error));
    }

    private void refreshTrustedWorkflows() {
        cloudStatus.setText("Получаю подтверждённые статусы GitHub…");
        async(() -> {
            StringBuilder out = new StringBuilder();
            for (String kind : new String[]{
                    GithubOperations.APK, GithubOperations.REPAIR, GithubOperations.TRAIN,
                    GithubOperations.STUDIO, GithubOperations.FREE_TESTS}) {
                try {
                    out.append(GithubOperations.report(kind, secrets.load())).append("\n\n");
                } catch (Exception error) {
                    out.append(kind).append(" — ошибка: ")
                            .append(error.getMessage()).append("\n\n");
                }
            }
            return out.toString();
        }, value -> {
            cloudStatus.setText(value);
            android.text.util.Linkify.addLinks(cloudStatus, android.text.util.Linkify.WEB_URLS);
            cloudStatus.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }, error -> cloudStatus.setText("GitHub недоступен: " + error));
    }

    private void updateRuns() {
        showResult("Сверяю реальные запуски GitHub…");
        async(() -> {
            List<GithubApi.RunInfo> list = GithubApi.runs(secrets.load());
            int reconciled = tasks.reconcile(list);
            org.json.JSONObject info = new org.json.JSONObject();
            info.put("updated", reconciled);
            org.json.JSONArray rows = new org.json.JSONArray();
            for (GithubApi.RunInfo run : list) {
                rows.put(new org.json.JSONObject()
                        .put("id", run.id).put("name", run.name)
                        .put("status", run.status).put("conclusion", run.conclusion)
                        .put("url", run.url));
            }
            info.put("runs", rows);
            return info.toString();
        }, json -> {
            runsContainer.removeAllViews();
            renderTasks();
            try {
                org.json.JSONObject data = new org.json.JSONObject(json);
                org.json.JSONArray rows = data.getJSONArray("runs");
                showResult("Запусков: " + rows.length() +
                        " • Обновлено задач: " + data.getInt("updated") +
                        ". Нажми на запуск для деталей; удерживай для GitHub.");
                if (rows.length() == 0) add(runsContainer,
                        text("Запусков пока нет.", 13, MUTED, false), 0, 0);
                for (int i = 0; i < rows.length() && i < 15; i++) {
                    org.json.JSONObject row = rows.getJSONObject(i);
                    String status = row.getString("status");
                    String conclusion = row.getString("conclusion");
                    long runId = row.getLong("id");
                    TextView item = text("● #" + runId + " • " + status +
                            " / " + conclusion + "\n" + row.optString("name"), 13,
                            "success".equals(conclusion) ? GREEN : ORANGE, false);
                    item.setBackground(shape(INPUT, 10));
                    pad(item, 12, 11);
                    add(runsContainer, item, 0, 9);
                    String url = row.getString("url");
                    item.setOnClickListener(v -> showJobDetails(runId));
                    item.setOnLongClickListener(v -> {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        return true;
                    });
                }
            } catch (Exception e) {
                showResult("Ошибка разбора ответа GitHub: " + e.getMessage());
            }
        }, error -> showResult("Сверка не выполнена: " + error));
    }

    private void publishTask(TaskStore.Task task) {
        if (task.issueNumber > 0) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://github.com/19wolfalone92/WOLF-AI/issues/" + task.issueNumber)));
            return;
        }
        if (GithubBacklog.suspectedSecret(task.text)) {
            showResult("В задании обнаружена строка, похожая на ключ или пароль. " +
                    "Удалите её перед публикацией на GitHub.");
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Опубликовать задачу публично?")
                .setMessage("Создать открытый GitHub Issue в WOLF-AI?\n\n" +
                        task.text + "\n\nЕго смогут читать другие люди. " +
                        "Он не выполняется автоматически. Нужен токен с Issues: Read and write.")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Опубликовать", (dialog, button) -> {
                    showResult("Отправляю только подтверждённое задание в GitHub…");
                    async(() -> {
                        GithubBacklog.IssueReceipt receipt = GithubBacklog.create(
                                task.text, task.project, task.mode, task.uid, secrets.load());
                        tasks.markIssue(task.id, task.uid, receipt.number);
                        return receipt.message;
                    }, message -> {
                        showResult(message);
                        renderTasks();
                    }, error -> showResult("Issue не создан: " + error));
                }).show();
    }

    private void refreshLearningStatus(String message) {
        if (learningStatus != null) {
            learningStatus.setText(message + "\nФактов: " + learning.factCount() +
                    " • примеров задач: " + learning.exampleCount());
        }
    }

    private void scheduleMetricoolPost() {
        if (!metricoolAuth.connected()) {
            metricoolStatus.setText("Сначала пройди OAuth авторизацию Metricool.");
            return;
        }
        MetricoolMcp.VideoPost request = new MetricoolMcp.VideoPost(
                metricoolBrand.getText().toString().trim(),
                metricoolVideo.getText().toString().trim(),
                metricoolText.getText().toString().trim(),
                metricoolWhen.getText().toString().trim(),
                "Europe/Minsk",
                metricoolNetwork.getSelectedItem().toString(),
                metricoolAuto.isChecked());
        String summary = "Бренд: " + request.brandId + "\nСеть: " + request.network +
                "\nВидео: " + request.url + "\nДата: " + request.dateIso +
                "\nПодпись: " + request.caption + "\nРежим: " +
                (request.autoPublish ? "АВТОПУБЛИКАЦИЯ" : "ЧЕРНОВИК") +
                "\n\nMetricool Free ограничивает число постов в месяц. " +
                "Видео должно быть доступно серверу по указанному URL.";
        new android.app.AlertDialog.Builder(this)
                .setTitle("Подтвердить действие Metricool?")
                .setMessage(summary)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(request.autoPublish ? "Да, запланировать публикацию" :
                        "Создать черновик", (dialog, button) -> {
                    metricoolStatus.setText("Отправляю один запрос в Metricool MCP…");
                    async(() -> metricoolMcp.schedule(request),
                            result -> metricoolStatus.setText(result),
                            error -> metricoolStatus.setText(
                                    "Нет подтверждения Metricool: " + error +
                                    "\nПроверь Planner до повторной отправки: " +
                                    "возможен неопределённый результат."));
                }).show();
    }

    private void handleMetricoolCallback(Intent intent) {
        if (intent == null || intent.getData() == null ||
                !"ai.wolf.autopilot".equals(intent.getData().getScheme()))
            return;
        Uri data = intent.getData();
        if (metricoolStatus == null) return;
        metricoolStatus.setText("Подтверждаю OAuth код на сервере Metricool…");
        async(() -> metricoolAuth.complete(data),
                result -> metricoolStatus.setText(result),
                error -> metricoolStatus.setText("Авторизация Metricool не завершена: " + error));
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleMetricoolCallback(intent);
    }

    private void shareSelectedVideo() {
        if (selectedVideo == null) {
            publicationStatus.setText("Сначала выбери видео MP4.");
            return;
        }
        String platform = PublicationCenter.NETWORKS[
                publicationSpinner.getSelectedItemPosition()];
        String caption = publicationCaption.getText().toString().trim();
        if (caption.length() > 2000) {
            publicationStatus.setText("Сократи подпись до 2000 символов.");
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setTitle("Передать видео в " + platform + "?")
                .setMessage(selectedVideo.description() + "\n\n" +
                        selectedVideo.warning() + "\n\n" +
                        "WOLF AI откроет приложение " + platform + ". " +
                        "Пост не появится автоматически: публикацию нужно подтвердить " +
                        "в выбранном сервисе. Продолжить?")
                .setNegativeButton("Отмена", null)
                .setPositiveButton("Открыть", (dialog, button) -> {
                    try {
                        if (!caption.isEmpty()) {
                            ClipboardManager cm =
                                    (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                            if (cm != null) cm.setPrimaryClip(
                                    ClipData.newPlainText("Video caption", caption));
                        }
                        PublicationCenter.share(this, selectedVideo, platform, caption);
                        publicationStatus.setText("Видео передано в " + platform +
                                " через Android. Заверши публикацию в соцсети; " +
                                "WOLF AI НЕ может подтвердить, что видео опубликовано.");
                    } catch (Exception e) {
                        publicationStatus.setText("Передача видео не удалась: " + e.getMessage());
                    }
                }).show();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == EXPORT_LEARNING) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri target = data.getData();
            refreshLearningStatus("Создаю локальную резервную копию…");
            async(() -> {
                byte[] bytes = learning.exportJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                try (java.io.OutputStream out = getContentResolver().openOutputStream(target, "w")) {
                    if (out == null) throw new IllegalStateException("Файл недоступен.");
                    out.write(bytes);
                    out.flush();
                }
                return "Резервная копия знаний записана: " + bytes.length + " байт.";
            }, result -> refreshLearningStatus(result),
                    error -> refreshLearningStatus("Ошибка экспорта: " + error));
            return;
        }
        if (requestCode == IMPORT_LEARNING) {
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            Uri source = data.getData();
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Заменить локальные знания из файла?")
                    .setMessage("Существующие заметки и примеры маршрутизации " +
                            "будут заменены содержимым выбранного JSON. " +
                            "GitHub-токен и чат останутся нетронутыми.")
                    .setNegativeButton("Отмена", null)
                    .setPositiveButton("Восстановить", (dialog, button) -> {
                        refreshLearningStatus("Проверяю выбранную резервную копию…");
                        async(() -> {
                            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                            try (java.io.InputStream in = getContentResolver().openInputStream(source)) {
                                if (in == null) throw new IllegalArgumentException("Файл не открывается.");
                                byte[] buffer = new byte[4096];
                                int n;
                                while ((n = in.read(buffer)) != -1) {
                                    if (output.size() + n > 120000)
                                        throw new IllegalArgumentException("Файл превышает лимит 120 КБ.");
                                    output.write(buffer, 0, n);
                                }
                            }
                            learning.importJson(output.toString("UTF-8"));
                            return "Локальные знания восстановлены.";
                        }, result -> refreshLearningStatus(result),
                                error -> refreshLearningStatus("Импорт отклонён: " + error));
                    }).show();
            return;
        }
        if (requestCode != PublicationCenter.PICK_VIDEO) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            publicationStatus.setText("Выбор видео отменён."); return;
        }
        Uri uri = data.getData();
        selectedVideo = null;
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // Temporary read grant remains valid while in foreground.
        }
        publicationStatus.setText("Проверяю MP4 и параметры видео…");
        async(() -> {
            PublicationCenter.VideoInfo video = PublicationCenter.inspect(this, uri);
            selectedVideo = video;
            return video.description() + "\n" + video.warning();
        }, result -> publicationStatus.setText(result),
           error -> publicationStatus.setText("Видео не принято: " + error));
    }

    private void renderTasks() {
        if (tasksContainer == null) return;
        tasksContainer.removeAllViews();
        List<TaskStore.Task> recent = tasks.recent();
        if (recent.isEmpty()) {
            add(tasksContainer, text("Локальных задач пока нет.", 13, MUTED, false), 0, 0);
            return;
        }
        for (TaskStore.Task task : recent) {
            LinearLayout box = vertical();
            box.setBackground(shape(INPUT, 11));
            pad(box, 12, 11);
            add(tasksContainer, box, 0, 9);
            add(box, text(task.text, 14, WHITE, true), 0, 4);
            String status;
            int stateColor;
            switch (task.status) {
                case TaskStore.SUBMITTED: status = "ОТПРАВЛЕНО — ждём GitHub"; stateColor = ORANGE; break;
                case TaskStore.RUNNING: status = "ВЫПОЛНЯЕТСЯ"; stateColor = CYAN; break;
                case TaskStore.SUCCESS: status = "ПРОВЕРКИ УСПЕШНЫ"; stateColor = GREEN; break;
                case TaskStore.FAILURE: status = "ПРОВЕРКА С ОШИБКОЙ"; stateColor = ORANGE; break;
                default: status = "ЧЕРНОВИК НА ТЕЛЕФОНЕ"; stateColor = MUTED;
            }
            add(box, text(task.project + " • " + task.mode + " • " + status +
                    (task.runId > 0 ? " • CI #" + task.runId : "") +
                    (task.issueNumber > 0 ? " • GitHub Issue #" + task.issueNumber : ""),
                    12, stateColor, true), 0, 8);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            Button check = button(TaskStore.DRAFT.equals(task.status) ? "▶ Проверить" : "↻ Статус", true);
            Button remove = button("Удалить", false);
            LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(48), 1);
            half.setMargins(0, 0, dp(7), 0);
            row.addView(check, half);
            row.addView(remove, new LinearLayout.LayoutParams(0, dp(48), 1));
            add(box, row, 0, 0);
            Button publish = button(task.issueNumber > 0 ?
                    "✓ Задание на GitHub #" + task.issueNumber :
                    "☁️ Отправить задачу в GitHub Issues", false);
            add(box, publish, 7, 0);
            publish.setOnClickListener(v -> publishTask(task));
            check.setOnClickListener(v -> {
                if (TaskStore.DRAFT.equals(task.status)) dispatchTask(task);
                else updateRuns();
            });
            remove.setOnClickListener(v -> {
                tasks.remove(task.id);
                renderTasks();
            });
        }
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        tasks.close();
        chat.close();
        learning.close();
        super.onDestroy();
    }
}
