package ai.wolf.autopilot;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * Foreground-only spoken turn recognition inside WOLF (no external dialog).
 * User must enable voice mode and grant RECORD_AUDIO runtime permission.
 *
 * This deliberately does not use hotword listening, background services,
 * screen reading or hidden microphone capture. System recognition service
 * may use its own cloud unless the available on-device recognizer is chosen.
 */
public final class InAppVoiceDialog {
    public interface Listener {
        void onListening();
        void onRecognized(String text);
        void onFailure(int code);
    }
    private final Activity activity;
    private final Listener listener;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean destroyed;
    private boolean offlineEngine;

    public InAppVoiceDialog(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }
    public boolean usesOfflineEngine() { return offlineEngine; }
    public boolean isListening() { return listening; }

    private void initialize() {
        if (recognizer != null) return;
        if (!SpeechRecognizer.isRecognitionAvailable(activity))
            throw new IllegalStateException("На Android не найдена служба распознавания речи.");
        offlineEngine = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
            try {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(activity);
                offlineEngine = true;
            } catch (RuntimeException unsupported) {
                // Fall back only to the normal installed Android recognizer.
            }
        }
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { listener.onListening(); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onError(int code) {
                boolean hadSession = listening;
                listening = false;
                if (!destroyed && hadSession) listener.onFailure(code);
            }
            @Override public void onResults(Bundle results) {
                if (destroyed || !listening) return;
                listening = false;
                ArrayList<String> matches = results == null ? null :
                        results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches == null || matches.isEmpty() || matches.get(0).trim().isEmpty())
                    listener.onFailure(SpeechRecognizer.ERROR_NO_MATCH);
                else listener.onRecognized(matches.get(0));
            }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    public void start() {
        if (destroyed || listening) return;
        initialize();
        Intent prompt = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        prompt.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        prompt.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        prompt.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        prompt.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offlineEngine);
        prompt.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true;
        try {
            recognizer.startListening(prompt);
        } catch (RuntimeException error) {
            listening = false;
            throw error;
        }
    }

    /** Cancels the active recognition turn; never retains captured audio. */
    public void stop() {
        listening = false;
        if (recognizer != null) recognizer.cancel();
    }

    public void close() {
        destroyed = true;
        stop();
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }
}
