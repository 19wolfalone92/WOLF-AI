package ai.wolf.autopilot;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Consent-once supervised batch uploader of PUBLIC MP4 URLs through Metricool.
 * Actual delivery is delegated to Metricool after each individually verified
 * MCP call. An uncertain response stops the queue; no silent retry or duplicate.
 */
public final class MetricoolBatchActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private EditText brand, rows;
    private Spinner network, timezone;
    private CheckBox automatic;
    private TextView progress;
    private Button submit;
    private boolean busy;
    private MetricoolOAuth oauth;
    private MetricoolMcp publisher;

    private int dp(int value) {
        return (int)(getResources().getDisplayMetrics().density * value + 0.5f);
    }
    private TextView text(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.rgb(231,241,252));
        t.setPadding(0, dp(6), 0, dp(8));
        return t;
    }
    private EditText input(String hint, boolean multiline) {
        EditText field = new EditText(this);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(Color.rgb(152,175,206));
        field.setHint(hint);
        field.setTextSize(15);
        if (multiline) {
            field.setInputType(InputType.TYPE_CLASS_TEXT |
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            field.setMinLines(7);
            field.setGravity(android.view.Gravity.TOP);
        } else field.setSingleLine(true);
        return field;
    }
    private Spinner spinner(String... values) {
        Spinner select = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, values);
        select.setAdapter(adapter);
        return select;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        oauth = new MetricoolOAuth(this);
        publisher = new MetricoolMcp(this, oauth);
        LinearLayout root = new LinearLayout(this);
        root.setPadding(dp(18), dp(13), dp(18), dp(24));
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10, 15, 28));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        root.addView(text("🐺 Публикации WOLF · пакетный режим", 21));
        root.addView(text("Подготовь до 12 публикаций, проверь весь список и " +
                "одним подтверждением отправь в официальный Metricool MCP. " +
                "Для публикации без присутствия телефона нужен подключённый " +
                "Metricool и доступные по публичному HTTPS ссылки MP4.", 14));
        root.addView(text("Бренд Metricool · числовой ID", 14));
        brand = input("ID бренда", false);
        brand.setInputType(InputType.TYPE_CLASS_NUMBER);
        root.addView(brand);
        root.addView(text("Социальная сеть", 14));
        network = spinner("tiktok", "youtube", "instagram", "facebook");
        root.addView(network);
        root.addView(text("Часовой пояс дат", 14));
        timezone = spinner("Europe/Minsk", "Europe/Amsterdam", "UTC");
        root.addView(timezone);
        automatic = new CheckBox(this);
        automatic.setText("Автоматически опубликовать в указанное время (иначе черновики)");
        automatic.setTextColor(Color.WHITE);
        automatic.setChecked(false);
        root.addView(automatic);
        root.addView(text("Одна строка = ДАТА С UTC-СМЕЩЕНИЕМ | HTTPS MP4 | ПОДПИСЬ", 14));
        rows = input("2026-12-01T23:00:00+03:00 | https://example.org/01.mp4 | Серия 1 #storyglitch\n" +
                "2026-12-02T01:00:00+03:00 | https://example.org/02.mp4 | Серия 2 #storyglitch", true);
        root.addView(rows, new LinearLayout.LayoutParams(-1, dp(190)));
        root.addView(text("Время должно соответствовать выбранному поясу. " +
                "Ссылки должны быть прямыми публичными MP4. " +
                "Видео с телефона напрямую не загружается. " +
                "Публикация зависит от настроек сети, лимитов Metricool и разрешений аккаунта.", 12));

        submit = new Button(this);
        submit.setAllCaps(false);
        submit.setText("🔎 Проверить и подтвердить весь пакет");
        root.addView(submit);
        progress = text("Ожидаю список. Никакие посты ещё не отправлены.", 14);
        progress.setTextIsSelectable(true);
        root.addView(progress);
        submit.setOnClickListener(v -> preview());
        if (state != null) {
            brand.setText(state.getString("brand", ""));
            rows.setText(state.getString("rows", ""));
            automatic.setChecked(state.getBoolean("automatic"));
            network.setSelection(state.getInt("network"));
            timezone.setSelection(state.getInt("timezone"));
        }
    }

    private void preview() {
        if (busy) return;
        if (!oauth.connected()) {
            progress.setText("Metricool не подключён. Открой ☰ → " +
                    "Дополнительные инструменты → Metricool OAuth.");
            return;
        }
        String brandId = brand.getText().toString().trim();
        if (!brandId.matches("[0-9]{1,20}")) {
            progress.setText("Сначала введи действительный числовой ID бренда.");
            return;
        }
        final String net = network.getSelectedItem().toString();
        final String zone = timezone.getSelectedItem().toString();
        final boolean auto = automatic.isChecked();
        final List<MetricoolBatchParser.Item> plans;
        try { plans = MetricoolBatchParser.parse(rows.getText().toString(), zone, Instant.now()); }
        catch (Exception error) { progress.setText("❌ " + error.getMessage()); return; }
        String review = "Бренд: " + brandId + "\n\n" +
                MetricoolBatchParser.preview(plans, net, zone, auto) +
                "\nПакет выполняется последовательно, без повторных отправок. " +
                "При ошибке либо неопределённом ответе выполнение остановится. " +
                "Не повторяй пакет, пока не проверишь Planner.";
        new AlertDialog.Builder(this)
                .setTitle("Подтверждение " + plans.size() + " публикаций")
                .setMessage(review)
                .setNegativeButton("Отмена", null)
                .setPositiveButton(auto ? "Разрешаю автопубликацию" :
                        "Создать черновики", (dialog, which) ->
                        schedule(brandId, net, zone, auto, plans)).show();
    }

    private void schedule(String brandId, String net, String zone, boolean auto,
                          List<MetricoolBatchParser.Item> plans) {
        if (busy) return;
        busy = true;
        submit.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progress.setText("Отправка 0/" + plans.size() +
                ". Оставь приложение открытым до завершения.");
        worker.execute(() -> {
            StringBuilder log = new StringBuilder();
            for (int i = 0; i < plans.size() && !Thread.currentThread().isInterrupted(); i++) {
                MetricoolBatchParser.Item item = plans.get(i);
                int current = i + 1;
                runOnUiThread(() -> progress.setText("⏳ Пост " +
                        current + "/" + plans.size() + " отправляется.\n\n" + log));
                try {
                    MetricoolMcp.VideoPost post = new MetricoolMcp.VideoPost(
                            brandId, item.url, item.caption, item.dateIso, zone, net, auto);
                    String result = publisher.schedule(post);
                    log.append("✅ ").append(current).append(": Metricool вернул ответ. ")
                            .append("Проверь статус поста в Planner.\n");
                } catch (Exception error) {
                    log.append("⛔ ").append(current)
                            .append(": результат неизвестен/ошибка (")
                            .append(error.getClass().getSimpleName())
                            .append("). Проверь Planner; автоматический повтор запрещён.\n")
                            .append("Пакет остановлен, оставшиеся строки НЕ отправлены.\n");
                    break;
                }
            }
            final String finalLog = log.toString();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                busy = false;
                submit.setEnabled(true);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                progress.setText(finalLog + "\nЭто ответы MCP, а не подтверждение выхода " +
                        "видео в TikTok. Сверь реальные публикации в Metricool.");
            });
        });
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("brand", brand.getText().toString());
        out.putString("rows", rows.getText().toString());
        out.putBoolean("automatic", automatic.isChecked());
        out.putInt("network", network.getSelectedItemPosition());
        out.putInt("timezone", timezone.getSelectedItemPosition());
    }
    @Override protected void onDestroy() {
        worker.shutdownNow(); // Never start new posts after screen is closed.
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        super.onDestroy();
    }
}
