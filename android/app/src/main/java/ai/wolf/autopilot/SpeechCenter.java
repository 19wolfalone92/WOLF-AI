package ai.wolf.autopilot;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/**
 * Android speech output for local WOLF answers. No paid voice API.
 * Android's selected TTS engine may use network/cloud voices; use an
 * installed offline voice if privacy or zero network usage matters.
 */
public final class SpeechCenter {
    private final Context context;
    private TextToSpeech engine;
    private boolean ready;
    private boolean loading;

    public SpeechCenter(Context context) {
        this.context = context.getApplicationContext();
    }

    public synchronized String speak(String text) {
        if (text == null || text.trim().isEmpty())
            return "Сначала получи текстовый ответ ИИ.";
        if (text.length() > 6000) text = text.substring(0, 6000);
        if (engine == null && !loading) {
            loading = true;
            TextToSpeech created = new TextToSpeech(context, status -> {
                synchronized (SpeechCenter.this) {
                    loading = false;
                    ready = status == TextToSpeech.SUCCESS;
                    if (ready && engine != null) {
                        int availability = engine.setLanguage(new Locale("ru", "RU"));
                        if (availability == TextToSpeech.LANG_NOT_SUPPORTED ||
                                availability == TextToSpeech.LANG_MISSING_DATA)
                            ready = false;
                        engine.setSpeechRate(0.94f);
                    }
                }
            });
            engine = created;
            return "🔊 Голосовой движок Android инициализируется. " +
                    "Нажми «Озвучить» повторно после загрузки. " +
                    "Для приватности выбери офлайн-голос в настройках Android.";
        }
        if (loading) return "Подготавливаю голосовой движок Android…";
        if (!ready || engine == null)
            return "В установленном Android TTS нет готового русского голоса. " +
                    "Установи русский голос в системных настройках синтеза речи.";
        String id = "wolf-" + System.currentTimeMillis();
        int result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        return result == TextToSpeech.SUCCESS ? "🔊 Ответ озвучивается." :
                "Не удалось запустить синтез речи.";
    }

    public synchronized void stop() {
        if (engine != null) engine.stop();
    }

    public synchronized void close() {
        if (engine != null) {
            engine.stop();
            engine.shutdown();
            engine = null;
        }
        ready = false;
        loading = false;
    }
}
