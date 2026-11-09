package ai.wolf.autopilot;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.StatFs;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

/** Actual device readings, not guesses based on a model-number table. */
public final class PhoneAiProfileActivity extends Activity {
    private TextView information;
    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private TextView text(String content,int size){
        TextView t=new TextView(this);t.setText(content);t.setTextSize(size);
        t.setTextColor(Color.rgb(234,244,255));t.setPadding(0,dp(8),0,dp(8));return t;
    }
    private Button button(String text){
        Button b=new Button(this);b.setText(text);b.setAllCaps(false);return b;
    }
    public static DeviceAiProfile.Plan current(Context c) {
        ActivityManager m=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        long ram=0,available=0;
        if(m!=null){
            ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
            m.getMemoryInfo(info);ram=info.totalMem;available=info.availMem;
        }
        long storage=new StatFs(c.getFilesDir().getAbsolutePath()).getAvailableBytes();
        return DeviceAiProfile.select(ram,available,storage);
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(19),dp(18),dp(19),dp(24));
        root.setBackgroundColor(Color.rgb(10,15,28));
        scroll.addView(root);setContentView(scroll);
        root.addView(text("📱 Реальная диагностика телефона для WOLF",22));
        root.addView(text("Данные считываются локально Android. " +
                "Никакие результаты не отправляются в GitHub, Hugging Face " +
                "или другие сервисы. Модель телефона не раскрывает точный " +
                "объём RAM: проверяем RAM непосредственно.",14));
        information=text("",14);
        root.addView(information);
        Button refresh=button("🔄 Проверить параметры заново");
        root.addView(refresh);refresh.setOnClickListener(v->refresh());
        Button catalog=button("🧠 Подобрать и скачать бесплатную модель");
        root.addView(catalog);
        catalog.setOnClickListener(v->startActivity(new Intent(this,ModelCatalogActivity.class)));
        root.addView(text("✅ Подбор — консервативная оценка RAM и накопителя. " +
                "WOLF не умеет удалённо измерить твой телефон через этот чат. " +
                "Для проверки реальной скорости и температуры после установки " +
                "модели потребуется тест непосредственно на устройстве. " +
                "Большие модели работают на CPU, без обещания GPU/NPU ускорения. " +
                "Пока не подтверждена совместимость конкретных Qwen3.5 " +
                "GGUF на этой сборке llama.cpp и Samsung.",13));
        refresh();
    }

    private void refresh(){
        try{
            ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
            if(am==null)throw new IllegalStateException("Android не предоставил RAM.");
            am.getMemoryInfo(info);
            StatFs stat=new StatFs(getFilesDir().getAbsolutePath());
            long free=stat.getAvailableBytes();
            DeviceAiProfile.Plan recommended=DeviceAiProfile.select(
                    info.totalMem,info.availMem,free);
            boolean arm64=false;
            for(String abi:Build.SUPPORTED_64_BIT_ABIS)
                if("arm64-v8a".equals(abi))arm64=true;
            String soc=Build.VERSION.SDK_INT>=Build.VERSION_CODES.S?
                    Build.SOC_MODEL : "неизвестно (старая версия Android)";
            PowerManager power=(PowerManager)getSystemService(Context.POWER_SERVICE);
            int thermal=power!=null && Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q?
                    power.getCurrentThermalStatus() : -1;
            String heat=thermal<0?"неизвестно":
                    thermal>=PowerManager.THERMAL_STATUS_SEVERE?
                            "высокий (снизь нагрузку)":
                    thermal>=PowerManager.THERMAL_STATUS_MODERATE?
                            "умеренный (лучше уменьшить глубину)":"низкий / нормальный";
            String report="📲 Модель Android: "+Build.MANUFACTURER+" "+Build.MODEL+
                    "\nВерсия Android: "+Build.VERSION.RELEASE+
                    "\nПроцессор: "+soc+
                    "\nАрхитектура ARM64: "+(arm64?"да":"не подтверждена")+
                    "\nЯдер CPU: "+Runtime.getRuntime().availableProcessors()+
                    "\nВсего RAM: "+DeviceAiProfile.formatBytes(info.totalMem)+
                    "\nДоступно RAM сейчас: "+DeviceAiProfile.formatBytes(info.availMem)+
                    "\nПорог нехватки RAM Android: "+
                        DeviceAiProfile.formatBytes(info.threshold)+
                    "\nСвободно на диске: "+DeviceAiProfile.formatBytes(free)+
                    "\nТепловая нагрузка: "+heat+
                    "\nТекущая модель: "+ModelVault.summary(this)+
                    "\n\n🐺 "+recommended.summary()+
                    "\n\n"+DeviceAiProfile.warning(recommended.recommended,recommended)+
                    "\n\nℹ️ Никаких подписок: все три Qwen3.5 работают локально " +
                    "после загрузки. Не используй .exe из видео на Android — " +
                    "нужен GGUF с встроенным движком WOLF.";
            information.setText(report);
        }catch(Exception error){
            information.setText("Не удалось получить характеристики: "+
                    error.getMessage());
        }
    }
}
