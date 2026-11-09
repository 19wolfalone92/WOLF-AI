package ai.wolf.autopilot;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Optional BYOK Hugging Face Inference Providers chat (fixed HTTPS endpoint).
 * Requires an explicit user-approved question. Does NOT send local history,
 * imported docs, Android state, GitHub token or any cookies.
 *
 * Free credits are limited; metered provider charges may apply depending
 * on the account. No automatic retry or background usage.
 */
public final class HfBrainClient {
    public static final String ENDPOINT =
            "https://router.huggingface.co/v1/chat/completions";
    private static final int DAILY_REQUEST_CAP = 12;
    private static final int MAX_REPLY_BYTES = 90_000;
    private HfBrainClient() {}

    private static String read(HttpURLConnection c) throws Exception {
        InputStream source = c.getResponseCode() >= 400 ?
                c.getErrorStream() : c.getInputStream();
        if (source == null) return "";
        try(InputStream in=source; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] chunk=new byte[4096]; int n;
            while ((n=in.read(chunk))!=-1) {
                if (out.size()+n>MAX_REPLY_BYTES)
                    throw new IllegalStateException("Ответ сервиса слишком большой.");
                out.write(chunk,0,n);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    /**
     * Persist the attempt count BEFORE writing over network, including
     * timeouts. No retries, concurrency guarded at the class monitor.
     */
    private static synchronized void reserve(Context app) {
        SharedPreferences p=app.getSharedPreferences("wolf_hf_calls",Context.MODE_PRIVATE);
        String day= LocalDate.now(ZoneOffset.UTC).toString();
        int used=day.equals(p.getString("day","")) ? p.getInt("used",0) : 0;
        if (used>=DAILY_REQUEST_CAP)
            throw new IllegalStateException("Лимит WOLF: 12 явных облачных запросов " +
                    "за сутки UTC. Лимиты/тарифы Hugging Face действуют отдельно.");
        if (!p.edit().putString("day",day).putInt("used",used+1).commit())
            throw new IllegalStateException("Не удалось записать лимит отправок.");
    }

    public static String ask(Context app, String question) throws Exception {
        String prompt=CloudBrainPolicy.question(question);
        CloudBrainStore vault=new CloudBrainStore(app);
        if (!vault.configured())
            throw new IllegalStateException("Облачный ИИ не подключён. Открой настройки.");
        String model=CloudBrainPolicy.model(vault.model());
        reserve(app.getApplicationContext());
        JSONObject request=new JSONObject()
                .put("model",model)
                .put("temperature",0.3)
                .put("max_tokens",600)
                .put("stream",false)
                .put("messages",new JSONArray()
                        .put(new JSONObject().put("role","system")
                                .put("content","Ты WOLF AI. Отвечай по-русски, точно и " +
                                        "практично. Не придумывай проверок, источников, " +
                                        "состояний GitHub и выполненных действий. " +
                                        "Этот вызов может ТОЛЬКО отвечать текстом: " +
                                        "никаких реальных инструментов он не выполняет. " +
                                        "Для задач давай проверяемый план по этапам."))
                        .put(new JSONObject().put("role","user")
                                .put("content",prompt)));
        HttpURLConnection c=(HttpURLConnection)new URL(ENDPOINT).openConnection();
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod("POST");
        c.setConnectTimeout(12000);
        c.setReadTimeout(45000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization","Bearer "+vault.token());
        c.setRequestProperty("Content-Type","application/json; charset=utf-8");
        c.setRequestProperty("Accept","application/json");
        c.setRequestProperty("User-Agent","WOLF-AI-Android/14.0");
        try {
            byte[] bytes=request.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>12_000)throw new IllegalArgumentException("Запрос слишком большой.");
            c.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=c.getOutputStream()){out.write(bytes);}
            int code=c.getResponseCode();
            String body=read(c);
            if(code != 200) {
                if(code==402||code==429)
                    throw new IllegalStateException("Hugging Face ограничил кредиты/частоту " +
                            "(HTTP "+code+"). Проверь баланс и тариф в HF; " +
                            "автоматических повторов нет.");
                throw new IllegalStateException("Провайдер Hugging Face ответил HTTP "+
                        code+". Проверь модель, разрешения токена и доступность.");
            }
            JSONObject response=new JSONObject(body);
            JSONArray choices=response.optJSONArray("choices");
            if(choices==null||choices.length()==0)
                throw new IllegalStateException("Модель вернула пустой ответ.");
            JSONObject message=choices.getJSONObject(0).optJSONObject("message");
            String result=message==null?"":message.optString("content","");
            if(result==null||result.trim().isEmpty())
                throw new IllegalStateException("Модель не вернула текст.");
            return "☁️ Hugging Face · "+model+"\n\n"+
                    result.substring(0,Math.min(9000,result.length()))+
                    "\n\n⚠️ Ответ облачной модели, не подтверждение выполнения действий.";
        } finally {
            c.disconnect();
        }
    }
}
