package com.example.temiapplication.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceInputManager {
    public interface Callback {
        void onReady();

        void onPartialResult(String text);

        void onResult(String text);

        void onError(String message);
    }

    private static final long RESTART_DELAY_MS = 700L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;
    private Callback callback;
    private boolean continuousMode;
    private boolean stopped = true;

    public VoiceInputManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static boolean isRecognitionAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void startSingleListening(Callback callback) {
        startListening(false, callback);
    }

    public void startContinuousListening(Callback callback) {
        startListening(true, callback);
    }

    public void stopListening() {
        stopped = true;
        continuousMode = false;
        handler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
        }
    }

    public void destroy() {
        stopListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    private void startListening(boolean continuousMode, Callback callback) {
        this.continuousMode = continuousMode;
        this.callback = callback;
        this.stopped = false;

        if (!isRecognitionAvailable(context)) {
            callback.onError("음성 인식을 사용할 수 없습니다.");
            return;
        }

        ensureRecognizer();
        speechRecognizer.cancel();
        speechRecognizer.startListening(createRecognizerIntent());
    }

    private void ensureRecognizer() {
        if (speechRecognizer != null) {
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (callback != null) {
                    callback.onReady();
                }
            }

            @Override
            public void onBeginningOfSpeech() {
                // no-op
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                // no-op
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
                // no-op
            }

            @Override
            public void onEndOfSpeech() {
                // no-op
            }

            @Override
            public void onError(int error) {
                if (stopped) {
                    return;
                }

                if (continuousMode && isRetryable(error)) {
                    scheduleRestart();
                    return;
                }

                if (callback != null) {
                    callback.onError(toErrorMessage(error));
                }
            }

            @Override
            public void onResults(Bundle results) {
                String text = firstResult(results);
                if (callback != null && !text.isEmpty()) {
                    callback.onResult(text);
                }
                if (continuousMode && !stopped) {
                    scheduleRestart();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = firstResult(partialResults);
                if (callback != null && !text.isEmpty()) {
                    callback.onPartialResult(text);
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
                // no-op
            }
        });
    }

    private Intent createRecognizerIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        return intent;
    }

    private String firstResult(Bundle bundle) {
        if (bundle == null) {
            return "";
        }

        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty() || matches.get(0) == null) {
            return "";
        }
        return matches.get(0).trim();
    }

    private boolean isRetryable(int error) {
        return error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
    }

    private void scheduleRestart() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (!stopped && speechRecognizer != null) {
                speechRecognizer.cancel();
                speechRecognizer.startListening(createRecognizerIntent());
            }
        }, RESTART_DELAY_MS);
    }

    private String toErrorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "마이크 입력 오류가 발생했습니다.";
            case SpeechRecognizer.ERROR_CLIENT:
                return "음성 인식 클라이언트 오류가 발생했습니다.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                return "마이크 권한이 필요합니다.";
            case SpeechRecognizer.ERROR_NETWORK:
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                return "음성 인식 네트워크 오류가 발생했습니다.";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "음성을 이해하지 못했습니다. 다시 말해주세요.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "음성 인식기가 사용 중입니다. 잠시 후 다시 시도해주세요.";
            case SpeechRecognizer.ERROR_SERVER:
                return "음성 인식 서버 오류가 발생했습니다.";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "음성이 감지되지 않았습니다. 다시 말해주세요.";
            default:
                return "음성 인식 오류가 발생했습니다. code=" + error;
        }
    }
}
