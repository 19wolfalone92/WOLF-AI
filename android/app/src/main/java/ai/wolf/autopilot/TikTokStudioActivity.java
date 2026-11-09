package ai.wolf.autopilot;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Direct TikTok Studio workflow without Metricool account or API:
 * inspect user-picked MP4, open first-party Studio browser with real chooser,
 * or share MP4 to native TikTok for user's final publish.
 */
public final class TikTokStudioActivity extends Activity {
    private static final int PICK=9441;
    public static final String STUDIO="https://www.tiktok.com/tiktokstudio/upload";
    private TextView info;
    private EditText caption;
    private PublicationCenter.VideoInfo selected;

    private int dp(int v){return Math.round(getResources().getDisplayMetrics().density*v);}
    private TextView text(String s,int size){
        TextView t=new TextView(this);
        t.setText(s);t.setTextSize(size);t.setTextColor(Color.rgb(240,246,255));
        t.setPadding(0,dp(8),0,dp(7));return t;
    }
    private Button button(String label) {
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);return b;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(17),dp(18),dp(22));
        root.setBackgroundColor(Color.rgb(10,15,28));
        scroll.addView(root);setContentView(scroll);
        root.addView(text("🎬 TikTok Studio без Metricool",23));
        root.addView(text("Прямая работа с собственным TikTok. " +
                "Для публикации авторизуйся в TikTok Studio и выбери MP4. " +
                "WOLF не хранит пароль и не передаёт ролики через Metricool.",15));
        info=text("Видео пока не выбрано.",14);root.addView(info);
        Button choose=button("📁 Выбрать и проверить MP4 на телефоне");
        root.addView(choose);
        choose.setOnClickListener(v->startActivityForResult(
                PublicationCenter.pickIntent(),PICK));
        root.addView(text("Подпись и хештеги (для отправки через TikTok)",14));
        caption=new EditText(this);
        caption.setTextColor(Color.WHITE);
        caption.setHint("Описание #storyglitch #mystery");
        caption.setHintTextColor(Color.LTGRAY);
        caption.setMinLines(3);caption.setMaxLines(6);
        root.addView(caption);
        Button inApp=button("🌐 Открыть TikTok Studio внутри WOLF");
        root.addView(inApp);
        inApp.setOnClickListener(v->{
            new AlertDialog.Builder(this)
                .setTitle("Открыть официальный TikTok Studio?")
                .setMessage("Авторизуйся вручную и включи JS для доверенной " +
                        "вкладки. TikTok Studio может не работать в Android WebView; " +
                        "при необходимости открой в Chrome. " +
                        "На странице нажми Upload и выбери MP4 через " +
                        "системные файлы Android. Финальная публикация " +
                        "и планирование выполняются на стороне TikTok.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Открыть",(d,w)->{
                    Intent browser=new Intent(this,BrowserActivity.class);
                    browser.putExtra(BrowserActivity.EXTRA_TARGET,STUDIO);
                    startActivity(browser);
                }).show();
        });
        Button chrome=button("↗ Открыть TikTok Studio в Chrome");
        root.addView(chrome);
        chrome.setOnClickListener(v->{
            try {startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(STUDIO)));}
            catch(ActivityNotFoundException e){
                info.setText("Нет доступного внешнего браузера.");}
        });
        Button share=button("📤 Отправить проверенный MP4 напрямую в TikTok");
        root.addView(share);
        share.setOnClickListener(v->{
            if(selected==null){
                info.setText("Сначала выбери файл MP4.");return;
            }
            String description=caption.getText().toString().trim();
            if(description.length()>2200){
                info.setText("Подпись превышает 2200 символов.");return;
            }
            new AlertDialog.Builder(this)
                .setTitle("Передать MP4 напрямую в TikTok?")
                .setMessage("Файл: "+selected.name+"\n\nПодпись: "+description+
                        "\n\nПосле передачи ты самостоятельно проверишь " +
                        "обложку, видимость и подпись и нажмёшь публикацию " +
                        "в TikTok. WOLF не заявит, что пост опубликован, " +
                        "только потому, что открыл приложение. Metricool не нужен.")
                .setNegativeButton("Отмена",null)
                .setPositiveButton("Передать",(d,w)->{
                    try {
                        PublicationCenter.share(this,selected,
                                PublicationCenter.TIKTOK,description);
                        info.setText("Видео передано в интерфейс публикации. " +
                                "Проверь результат в TikTok вручную.");
                    }catch(Exception ex){
                        info.setText("TikTok не открылся: "+ex.getMessage());
                    }
                }).show();
        });
        root.addView(text("✅ Контроль перед публикацией: " +
                "корректный MP4, звук, первый кадр, субтитры, обложка, " +
                "описание, аккаунт и приватность. " +
                "Прямая автоматическая публикация без твоего участия " +
                "доступна через официальный TikTok Content Posting API " +
                "только после подключения одобренного developer-приложения " +
                "с video.publish и аудита TikTok. Этот экран не выдаёт " +
                "ручной шаг за автопубликацию.",13));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=PICK)return;
        if(result!=RESULT_OK||data==null||data.getData()==null){
            info.setText("Выбор видео отменён.");return;
        }
        try {
            selected=PublicationCenter.inspect(this,data.getData());
            String warning=selected.openingBrightness>=0&&
                    selected.openingBrightness<35?
                    "\n⚠️ Начальный кадр очень тёмный: проверь обложку.":"";
            info.setText("✅ "+selected.name+"\n"+
                    selected.width+"×"+selected.height+" · "+
                    selected.durationMs/1000+" с · "+selected.videoCodec+
                    (selected.hasAudio?" · со звуком":" · без обнаруженного звука")+
                    warning);
        }catch(Exception ex){
            selected=null;
            info.setText("Не удалось проверить MP4: "+ex.getMessage());
        }
    }
}
