package ai.wolf.autopilot;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.Set;

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
    private String queuedText;
    private Runnable afterSpeech;
    private StatusListener statusListener;
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    public interface StatusListener {
        void onStatus(String message);
    }

    public synchronized void setStatusListener(StatusListener callback) {
        statusListener = callback;
    }

    private void notifyStatus(String value) {
        mainThread.post(() -> {
            StatusListener callback;
            synchronized (SpeechCenter.this) { callback = statusListener; }
            if (callback != null) callback.onStatus(value);
        });
    }

    public SpeechCenter(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Called on UI thread after the complete Android TTS utterance. */
    public synchronized void setOnUtteranceDone(Runnable callback) {
        afterSpeech = callback;
        if (engine != null && ready) installListener();
    }

    private void installListener() {
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) {}
            @Override public void onDone(String utteranceId) {
                mainThread.post(() -> {
                    Runnable callback;
                    synchronized (SpeechCenter.this) { callback = afterSpeech; }
                    if (callback != null) callback.run();
                });
            }
            @Override public void onError(String utteranceId) {
                // Recognition is NOT restarted after TTS failure.
                notifyStatus("🔈 Android TTS не смог произнести ответ. " +
                        "Проверь выбранный голос и громкость мультимедиа.");
            }
        });
    }

    public synchronized String speak(String text) {
        if (text == null || text.trim().isEmpty())
            return "Сначала получи текстовый ответ ИИ.";
        if (text.length() > 6000) text = text.substring(0, 6000);
        if (engine == null && !loading) {
            loading = true;
            queuedText = text;
            // Queue the first reply while Android initializes the TTS engine.
            // Never require the user to tap a second time to hear it.
            engine = new TextToSpeech(context, status -> {
                synchronized (SpeechCenter.this) {
                    loading = false;
                    ready = status == TextToSpeech.SUCCESS;
                    if (ready && engine != null) {
                        engine.setSpeechRate(0.94f);
                        installListener();
                    }
                    if (!ready) queuedText = null;
                }
                if (status != TextToSpeech.SUCCESS)
                    notifyStatus("🔈 Голос Android не инициализировался. " +
                            "Открой настройки синтеза речи и установи русский голос.");
                if (status == TextToSpeech.SUCCESS) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        synchronized (SpeechCenter.this) {
                            if (queuedText != null && engine != null && ready) {
                                String pending = queuedText;
                                queuedText = null;
                                speak(pending);
                            }
                        }
                    });
                }
            });
            return "🔊 Голос готовится. Первый ответ будет озвучен автоматически, " +
                    "когда Android завершит инициализацию.";
        }
        if (loading) {
            queuedText = text;
            return "🔊 Запомнил текст: озвучу после запуска голосового движка.";
        }
        if (!ready || engine == null) {
            String problem="В установленном Android не удалось запустить TTS. " +
                    "Открой настройки синтеза речи и установи голос.";
            notifyStatus(problem);
            return problem;
        }
        if (!configureVoice(text)) {
            String problem="Язык ответа недоступен в установленном Android TTS. " +
                    "Установи русский или английский голос в системных настройках.";
            notifyStatus(problem);
            return problem;
        }
        String id = "wolf-" + System.currentTimeMillis();
        int result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id);
        if (result != TextToSpeech.SUCCESS) {
            notifyStatus("🔈 Android отклонил озвучивание. Проверь TTS и мультимедиа.");
            return "Не удалось запустить синтез речи.";
        }
        return "🔊 Ответ озвучивается.";
    }

    private boolean configureVoice(String text) {
        Locale language = text.matches("(?s).*[\\u0400-\\u04ff].*") ?
                new Locale("ru", "RU") : Locale.US;
        int availability = engine.setLanguage(language);
        if (availability == TextToSpeech.LANG_NOT_SUPPORTED ||
                availability == TextToSpeech.LANG_MISSING_DATA) return false;
        // Prefer downloaded, device-local voices. An installed engine can still
        // have network access; voice selection alone is not a privacy guarantee.
        try {
            Set<Voice> voices = engine.getVoices();
            if (voices != null) {
                for (Voice voice : voices) {
                    if (voice != null && voice.getLocale() != null &&
                            language.getLanguage().equals(voice.getLocale().getLanguage()) &&
                            !voice.isNetworkConnectionRequired()) {
                        engine.setVoice(voice);
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
            // Some OEM TTS engines cannot enumerate voices.
        }
        return true;
    }

    public synchronized void stop() {
        queuedText = null;
        if (engine != null) engine.stop();
    }

    public synchronized void close() {
        queuedText = null;
        if (engine != null) {
            engine.stop();
            engine.shutdown();
            engine = null;
        }
        ready = false;
        loading = false;
        afterSpeech = null;
        statusListener = null;
    }
}
