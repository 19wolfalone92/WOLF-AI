package ai.wolf.autopilot;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owner-approved download from official model publisher over Wi-Fi only.
 * Uses Android DownloadManager so the transfer survives Activity lifecycle.
 * Never downloads a model or swaps the working model without direct consent.
 */
public final class ModelCatalogActivity extends Activity {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private DownloadManager manager;
    private TextView status;
    private Button install;
    private boolean busy;

    private int dp(int x) { return Math.round(getResources().getDisplayMetrics().density*x); }
    private TextView txt(String value, int size) {
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(Color.rgb(236,245,255));
        t.setPadding(0,dp(8),0,dp(9));return t;
    }
    private Button button(String value) {
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);return b;
    }
    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("wolf_model_catalog_v15",MODE_PRIVATE);
    }
    private long job() { return prefs().getLong("job_id",-1L); }
    private OfflineModelCatalog.Entry selected() {
        return OfflineModelCatalog.get(prefs().getString("entry","QWEN35_2"));
    }
    private void clearJob() {
        prefs().edit().remove("job_id").remove("entry").commit();
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        manager=(DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        ScrollView view=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setBackgroundColor(Color.rgb(10,15,28));
        root.setPadding(dp(18),dp(14),dp(18),dp(22));
        root.setOrientation(LinearLayout.VERTICAL);view.addView(root);setContentView(view);
        root.addView(txt("🐺 Бесплатные модели — на самом телефоне",22));
        root.addView(txt("Никакой подписки или облачного API: модель " +
                "один раз скачивается по Wi-Fi и затем работает офлайн. " +
                "Скачиваются официальные GGUF, проверяется SHA-256. " +
                "Встроенная Qwen 0.5B всегда остаётся в APK.",15));
        root.addView(txt("📱 Сначала измеряем реальную RAM и доступный накопитель. " +
                "Qwen3.5 0.8B: ~563 МБ; Qwen3.5 2B: ~1.28 ГБ; " +
                "Qwen3.5 4B: ~2.74 ГБ. Большая модель требует больше RAM " +
                "и может работать медленно. Первая загрузка — по Wi-Fi.",13));
        DeviceAiProfile.Plan advice=PhoneAiProfileActivity.current(this);
        root.addView(txt("🐺 "+advice.summary(),15));
        Button recommended=button("✅ Рекомендуемая: "+advice.recommended.title);
        root.addView(recommended);
        recommended.setOnClickListener(v->confirmDownload(advice.recommended));
        Button device=button("📱 Проверить реальные параметры телефона");
        root.addView(device);
        device.setOnClickListener(v->startActivity(
                new Intent(this,PhoneAiProfileActivity.class)));
        root.addView(txt("Текущая модель: "+ModelVault.summary(this),13));
        status=txt("Проверяю загрузки…",14);root.addView(status);
        root.addView(txt("Новые бесплатные Qwen3.5 · разговор и рассуждение",16));
        Button compact=button("⚡ Qwen3.5 0.8B · ~563 МБ");
        root.addView(compact);
        compact.setOnClickListener(v->confirmDownload(OfflineModelCatalog.Entry.QWEN35_08));
        Button balanced=button("💬 Qwen3.5 2B · ~1.28 ГБ");
        root.addView(balanced);
        balanced.setOnClickListener(v->confirmDownload(OfflineModelCatalog.Entry.QWEN35_2));
        Button powerful=button("🧠 Qwen3.5 4B · ~2.74 ГБ");
        root.addView(powerful);
        powerful.setOnClickListener(v->confirmDownload(OfflineModelCatalog.Entry.QWEN35_4));
        root.addView(txt("Другие офлайн-модели Qwen3",15));
        Button small=button("Qwen3 1.7B · ~1.3 ГБ");
        root.addView(small);
        small.setOnClickListener(v->confirmDownload(OfflineModelCatalog.Entry.QWEN3_17));
        Button bigger=button("Qwen3 4B · ~2.5 ГБ");
        root.addView(bigger);
        bigger.setOnClickListener(v->confirmDownload(OfflineModelCatalog.Entry.QWEN3_4));
        Button check=button("🔄 Проверить загрузку");
        root.addView(check);check.setOnClickListener(v->refresh());
        install=button("✅ Проверить SHA-256 и установить скачанную модель");
        root.addView(install);install.setOnClickListener(v->confirmInstall());
        Button cancel=button("✕ Отменить текущую загрузку (модель останется)");
        root.addView(cancel);cancel.setOnClickListener(v->confirmCancel());
        root.addView(txt("Загрузка использует Android DownloadManager и только Wi-Fi. " +
                "Если закрыть WOLF, скачивание может продолжиться. " +
                "После окончания открой этот экран и нажми «Проверить SHA-256 и установить». " +
                "WOLF не делает никаких платежей, не открывает подписок и " +
                "не переключает модель без твоего подтверждения.",12));
        refresh();
    }
    @Override public void onResume() { super.onResume();if(status!=null)refresh(); }

    private void confirmDownload(OfflineModelCatalog.Entry entry) {
        if(busy)return;
        if(job()>0L){status.setText("Уже есть загрузка. Проверь или отмени её.");return;}
        ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        long ram=0;
        if(am!=null) {
            ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();
            am.getMemoryInfo(m);ram=m.totalMem;
        }
        String memory=ram==0?"RAM не определена":
                String.format(Locale.ROOT,"Общая RAM: %.1f ГБ.",ram/1e9);
        DeviceAiProfile.Plan advice=PhoneAiProfileActivity.current(this);
        new AlertDialog.Builder(this)
                .setTitle("Скачать "+entry.title+" по Wi-Fi?")
                .setMessage(OfflineModelCatalog.describe(entry)+"\n"+
                        memory+"\n\n"+DeviceAiProfile.warning(entry,advice)+
                        "\n\nПонадобится дополнительно около "+
                        String.format(Locale.ROOT,"%.1f ГБ",entry.maxBytes*2.0/1e9)+
                        " для скачивания и проверенной установки. " +
                        "Сеть мобильного оператора не используется. " +
                        "Интернет-трафик по тарифу твоего Wi-Fi может оплачиваться.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Скачать",(d,w)->beginDownload(entry)).show();
    }

    private void beginDownload(OfflineModelCatalog.Entry entry) {
        if(!OfflineModelCatalog.trusted(entry) || job()>0L) return;
        try {
            File dest=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if(dest==null || !dest.isDirectory())
                throw new IllegalStateException("Нет папки для загрузки.");
            long needed=entry.maxBytes*2+350_000_000L;
            long available=new StatFs(dest.getAbsolutePath()).getAvailableBytes();
            if(available<needed)
                throw new IllegalStateException("Недостаточно свободного места: нужно около "+
                        String.format(Locale.ROOT,"%.1f ГБ",needed/1e9)+
                        " для загрузки и установки.");
            File target=new File(dest,entry.downloadName);
            if(target.exists() && !target.delete())
                throw new IllegalStateException("Не удалось очистить неполную загрузку.");
            DownloadManager.Request request=new DownloadManager.Request(Uri.parse(entry.url));
            request.setTitle("WOLF · "+entry.title);
            request.setDescription("Официальная бесплатная GGUF модель. Только Wi-Fi.");
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
            request.setAllowedOverMetered(false);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalFilesDir(
                    this,Environment.DIRECTORY_DOWNLOADS,entry.downloadName);
            long id=manager.enqueue(request);
            if(!prefs().edit().putLong("job_id",id)
                    .putString("entry",entry.name()).commit()) {
                manager.remove(id);
                throw new IllegalStateException("Не удалось сохранить номер загрузки.");
            }
            refresh();
        } catch(Exception ex) {
            status.setText("Загрузка не запущена: "+ex.getMessage());
        }
    }

    private int currentStatus() {
        long id=job();
        if(id<=0)return -1;
        try(Cursor c=manager.query(new DownloadManager.Query().setFilterById(id))) {
            if(c!=null && c.moveToFirst())
                return c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
        } catch(Exception ignored) {}
        return -1;
    }

    private void refresh() {
        if(busy)return;
        long id=job();
        if(id<=0) {
            status.setText("● Нет активной загрузки. Выбери бесплатную модель.");
            install.setEnabled(false);return;
        }
        int state=currentStatus();
        install.setEnabled(state==DownloadManager.STATUS_SUCCESSFUL);
        OfflineModelCatalog.Entry entry=selected();
        if(state==DownloadManager.STATUS_SUCCESSFUL) {
            status.setText("✅ "+entry.title+" скачана. " +
                    "Нажми «Проверить SHA-256 и установить».");
            return;
        }
        if(state==DownloadManager.STATUS_FAILED || state==-1) {
            status.setText("⛔ Загрузка прервалась. Отмени её и попробуй ещё раз.");
            return;
        }
        try(Cursor c=manager.query(new DownloadManager.Query().setFilterById(id))) {
            if(c!=null && c.moveToFirst()) {
                long current=c.getLong(c.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                long total=c.getLong(c.getColumnIndexOrThrow(
                        DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                status.setText("⬇️ "+entry.title+" · "+
                        String.format(Locale.ROOT,"%.0f МБ",current/1e6) +
                        (total>0?" / "+String.format(Locale.ROOT,"%.0f МБ",total/1e6):"")+
                        (state==DownloadManager.STATUS_PAUSED?
                                "\nОжидает Wi-Fi/сеть или разрешения DownloadManager.":""));
            }
        } catch(Exception ex) {
            status.setText("Загрузка продолжается. Проверка статуса недоступна.");
        }
    }

    private void confirmInstall() {
        if(busy || currentStatus()!=DownloadManager.STATUS_SUCCESSFUL)return;
        final OfflineModelCatalog.Entry entry=selected();
        new AlertDialog.Builder(this)
                .setTitle("Активировать "+entry.title+"?")
                .setMessage("WOLF повторно прочитает файл, сверит с " +
                        "официальным SHA-256 и только после проверки заменит " +
                        "текущую импортированную модель. Встроенная 0.5B " +
                        "останется в APK. Установка может потребовать " +
                        "дополнительное место и время.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Проверить и установить",(d,w)->{
                    busy=true;install.setEnabled(false);
                    status.setText("🔒 Потоковая проверка SHA-256 и установка модели…");
                    worker.execute(()->{
                        String outcome;
                        boolean ok=false;
                        try {
                            File folder=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                            if(folder==null)throw new IllegalStateException("Папка недоступна.");
                            File file=new File(folder,entry.downloadName);
                            outcome=ModelVault.installVerifiedDownload(
                                    getApplicationContext(),file,entry);
                            ok=true;
                        } catch(Exception error) {
                            outcome="⛔ Модель не установлена: "+error.getMessage();
                        }
                        final boolean success=ok;
                        final String message=outcome;
                        runOnUiThread(()->{
                            if(isFinishing()||isDestroyed())return;
                            busy=false;status.setText(message);
                            if(success)clearJob();
                            install.setEnabled(!success);
                        });
                    });
                }).show();
    }

    private void confirmCancel() {
        if(busy || job()<=0)return;
        new AlertDialog.Builder(this).setTitle("Отменить скачивание?")
                .setMessage("Загрузка будет удалена, работающая модель не изменится.")
                .setNegativeButton("Нет",null)
                .setPositiveButton("Отменить",(d,w)->{
                    manager.remove(job());
                    try {
                        File dir=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                        if(dir!=null)new File(dir,selected().downloadName).delete();
                    } catch(Exception ignored) {}
                    clearJob();refresh();
                }).show();
    }
    @Override protected void onDestroy() {
        worker.shutdownNow();super.onDestroy();
    }
}
