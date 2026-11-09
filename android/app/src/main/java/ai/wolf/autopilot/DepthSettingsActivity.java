package ai.wolf.autopilot;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

/** Actual control over on-device inference passes, output and default policy. */
public final class DepthSettingsActivity extends Activity {
    private int steps;
    private int tokens;
    private TextView summary;
    private SeekBar stepBar;
    private SeekBar tokenBar;
    private CheckBox automatic;

    private int dp(int x) { return Math.round(getResources().getDisplayMetrics().density*x); }
    private TextView text(String s,int size) {
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);
        v.setTextColor(Color.rgb(234,246,255));v.setPadding(0,dp(7),0,dp(7));
        return v;
    }
    private Button button(String s) {
        Button b=new Button(this);b.setText(s);b.setAllCaps(false);return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ThinkingDepth.Settings current=ThinkingDepth.load(this);
        steps=current.steps;tokens=current.tokens;
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19),dp(20),dp(19),dp(26));
        root.setBackgroundColor(Color.rgb(10,15,28));
        scroll.addView(root);setContentView(scroll);
        root.addView(text("🧠 Настройка глубины WOLF",23));
        root.addView(text("Действительно выполняет 1–4 отдельных прохода " +
                "локальной модели. Чем больше проходов и токенов, тем дольше " +
                "ответ и выше нагрузка на телефон. Никаких платных API.",15));
        summary=text("",17);root.addView(summary);
        root.addView(text("Пресеты",16));
        for(int j=1;j<=4;j++) {
            final int level=j;
            Button preset=button(j==1?"⚡ Быстро (1 проход)":
                    j==2?"⚖️ Сбалансировано (2 прохода)":
                    j==3?"🔍 Глубоко (3 прохода)":"🧠 Эксперт (4 прохода)");
            root.addView(preset);
            preset.setOnClickListener(v->{
                ThinkingDepth.Settings p=ThinkingDepth.preset(level);
                steps=p.steps;tokens=p.tokens;
                stepBar.setProgress(steps-1);
                tokenBar.setProgress((tokens-128)/64);
                refresh();
            });
        }
        root.addView(text("Число реальных проходов (1–4)",15));
        stepBar=new SeekBar(this);
        stepBar.setMax(3);stepBar.setProgress(steps-1);
        root.addView(stepBar);
        stepBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar view,int value,boolean fromUser){
                if(fromUser)steps=value+1;
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar view){}
            @Override public void onStopTrackingTouch(SeekBar view){}
        });
        root.addView(text("Максимум токенов одного ответа (128–768)",15));
        tokenBar=new SeekBar(this);
        tokenBar.setMax(10);tokenBar.setProgress((tokens-128)/64);
        root.addView(tokenBar);
        tokenBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar view,int value,boolean fromUser){
                if(fromUser) tokens=128+value*64;
                refresh();
            }
            @Override public void onStartTrackingTouch(SeekBar view){}
            @Override public void onStopTrackingTouch(SeekBar view){}
        });
        automatic=new CheckBox(this);
        automatic.setText("Использовать выбранную глубину для ВСЕХ обычных вопросов");
        automatic.setTextColor(Color.rgb(238,246,255));
        automatic.setChecked(current.automatic);
        root.addView(automatic);
        root.addView(text("Если выключено, обычные вопросы обрабатываются " +
                "быстро, а многошаговый режим включается командами " +
                "«Подумай глубже: ...» и «Реши подробно: ...». " +
                "Самопроверка WOLF не является независимой экспертизой.",13));
        Button save=button("✅ Сохранить глубину");
        root.addView(save);
        save.setOnClickListener(v->{
            try {
                ThinkingDepth.save(this,new ThinkingDepth.Settings(
                        steps,tokens,automatic.isChecked()));
                finish();
            } catch(Exception e) {
                summary.setText("Не удалось сохранить: "+e.getMessage());
            }
        });
        refresh();
    }
    private void refresh() {
        if(summary!=null)summary.setText(
                new ThinkingDepth.Settings(steps,tokens,
                        automatic!=null&&automatic.isChecked()).label());
    }
}
