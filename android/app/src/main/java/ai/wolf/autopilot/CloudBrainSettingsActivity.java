package ai.wolf.autopilot;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Cloud inference is OFF by default and configured only by the device owner. */
public final class CloudBrainSettingsActivity extends Activity {
    private CloudBrainStore store;
    private EditText token;
    private EditText model;
    private TextView status;

    private int dp(int n) {
        return Math.round(getResources().getDisplayMetrics().density*n);
    }
    private TextView text(String value,int size) {
        TextView t=new TextView(this);
        t.setText(value); t.setTextColor(Color.rgb(236,247,255));
        t.setTextSize(size);t.setPadding(0,dp(7),0,dp(9));return t;
    }
    private Button button(String label) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);
        return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        store=new CloudBrainStore(this);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(18),dp(18),dp(26));
        root.setBackgroundColor(Color.rgb(10,15,28));
        scroll.addView(root);setContentView(scroll);
        root.addView(text("☁️ Умная модель Hugging Face",22));
        root.addView(text("По умолчанию все сообщения остаются у локальной Qwen. " +
                "Подключи отдельный токен Hugging Face с доступом к Inference Providers. " +
                "Для облачного запроса используй команду «Спроси облачный ИИ: ...». " +
                "WOLF всегда отдельно покажет запрос перед отправкой.",15));
        root.addView(text("Облако получит ТОЛЬКО одобренный текст вопроса. " +
                "История переписки, документы, заметки, cookies и GitHub PAT не отправляются. " +
                "Ответ не управляет Android и не публикует видео.",14));
        root.addView(text("⚠️ На бесплатном аккаунте HF предусмотрены небольшие " +
                "ежемесячные вычислительные кредиты (сумма может меняться). " +
                "При исчерпании возможны платные тарифы — проверь свой биллинг на HF. " +
                "Сам WOLF ограничивает запросы 12 попытками в сутки UTC и не " +
                "повторяет их автоматически.",13));
        status=text(store.configured() ?
                "🔐 Токен уже зашифрован в Android Keystore." :
                "● Облачный ИИ не подключён.",15);
        root.addView(status);
        root.addView(text("Токен hf_… (не вводи его в чат)",14));
        token=new EditText(this);
        token.setHint("hf_...");token.setTextColor(Color.WHITE);
        token.setHintTextColor(Color.GRAY);
        token.setInputType(InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(token);
        root.addView(text("Модель Inference Providers",14));
        model=new EditText(this);
        model.setTextColor(Color.WHITE);
        model.setText(store.configured() ? store.model() :
                "openai/gpt-oss-20b:cheapest");
        model.setSingleLine(true);
        root.addView(model);
        Button save=button("🔒 Сохранить настройки облачного ИИ");
        root.addView(save);
        save.setOnClickListener(v -> {
            try {
                store.save(token.getText().toString(),model.getText().toString());
                token.setText("");
                status.setText("✅ Подключено. Теперь облачный запрос " +
                        "возможен только после отдельного подтверждения.");
            } catch (Exception error) {
                status.setText("Не удалось сохранить: " +
                        error.getClass().getSimpleName() +
                        (error instanceof IllegalArgumentException?
                                ": "+error.getMessage():""));
            }
        });
        Button delete=button("🗑 Отключить облако и удалить сохранённый токен");
        root.addView(delete);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Удалить локальный токен HF?")
                .setMessage("Облачные запросы перестанут работать. " +
                        "Локальная Qwen останется доступной.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Удалить токен",(dialog,which)->{
                    store.clear();token.setText("");
                    status.setText("Облачный ИИ отключён. Токен удалён из хранилища.");
                }).show());
    }
}
