package com.kidsFriend.voice;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class VoiceInputManager {
    private static final String TAG = "VoiceInputManager";

    public interface Callback {
        void onReady();
        void onPartialResult(String text);
        void onResult(String text);
        void onError(String message);
    }

    private static final long RESTART_DELAY_MS = 700L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AudioManager audioManager;

    private SpeechRecognizer speechRecognizer;
    private Callback callback;
    private boolean continuousMode;
    private boolean stopped = true;

    // Temi 자체 ASR 시스템이 마이크를 선점하지 못하도록 오디오 포커스를 명시적으로 요청한다.
    // AUDIOFOCUS_LOSS 이벤트를 수신하면 재시도를 스케줄링한다.
    private final AudioManager.OnAudioFocusChangeListener audioFocusListener = focusChange -> {
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            Log.w(TAG, "Audio focus lost (focusChange=" + focusChange + "), scheduling restart.");
            if (!stopped && continuousMode) {
                scheduleRestart();
            }
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            Log.d(TAG, "Audio focus regained.");
            if (!stopped && speechRecognizer == null) {
                ensureRecognizer();
                speechRecognizer.startListening(createRecognizerIntent());
            }
        }
    };

    public VoiceInputManager(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public static boolean isRecognitionAvailable(Context context) {
        boolean speechRecognizerAvailable = SpeechRecognizer.isRecognitionAvailable(context);
        Intent intent = new Intent(RecognitionService.SERVICE_INTERFACE);
        List<ResolveInfo> services = context.getPackageManager()
                .queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);

        Log.d(TAG, "SpeechRecognizer.isRecognitionAvailable = " + speechRecognizerAvailable);
        Log.d(TAG, "RecognitionService count = " + services.size());
        for (ResolveInfo service : services) {
            if (service.serviceInfo == null) continue;
            Log.d(TAG, "RecognitionService = "
                    + service.serviceInfo.packageName + "/"
                    + service.serviceInfo.name);
        }

        return speechRecognizerAvailable;
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
        releaseAudioFocus();
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
            Log.w(TAG, "Cannot start listening because SpeechRecognizer is unavailable.");
            if (callback != null) {
                callback.onError("이 기기에서는 음성 인식을 사용할 수 없습니다. 텍스트 입력으로 진행해주세요.");
            }
            return;
        }

        // Temi 시스템 ASR보다 먼저 마이크를 선점한다.
        // 포커스 획득 실패 시에도 SpeechRecognizer 시작을 시도한다(Temi 환경에서의 호환성).
        int focusResult = audioManager.requestAudioFocus(
                audioFocusListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
        );
        Log.d(TAG, "requestAudioFocus result = " + focusResult
                + " (GRANTED=" + AudioManager.AUDIOFOCUS_REQUEST_GRANTED + ")");

        ensureRecognizer();
        speechRecognizer.cancel();
        Log.d(TAG, "startListening continuousMode = " + continuousMode);
        speechRecognizer.startListening(createRecognizerIntent());
    }

    private void releaseAudioFocus() {
        audioManager.abandonAudioFocus(audioFocusListener);
    }

    private void ensureRecognizer() {
        if (speechRecognizer != null) {
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "onReadyForSpeech");
                if (callback != null) callback.onReady();
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {}

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {}

            @Override
            public void onError(int error) {
                Log.w(TAG, "onError code = " + error + ", message = " + toErrorMessage(error));
                if (stopped) return;

                if (continuousMode && isRetryable(error)) {
                    scheduleRestart();
                    return;
                }

                // 오디오 포커스를 잃어서 생긴 일시적 오류는 재시도
                if (error == SpeechRecognizer.ERROR_AUDIO && continuousMode) {
                    scheduleRestart();
                    return;
                }

                if (callback != null) callback.onError(toErrorMessage(error));
            }

            @Override
            public void onResults(Bundle results) {
                String text = firstResult(results);
                Log.d(TAG, "onResults text = " + text);
                if (callback != null && !text.isEmpty()) callback.onResult(text);
                if (continuousMode && !stopped) scheduleRestart();
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                String text = firstResult(partialResults);
                Log.d(TAG, "onPartialResults text = " + text);
                if (callback != null && !text.isEmpty()) callback.onPartialResult(text);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private Intent createRecognizerIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREA.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해주세요");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        return intent;
    }

    private String firstResult(Bundle bundle) {
        if (bundle == null) return "";
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty() || matches.get(0) == null) return "";
        return matches.get(0).trim();
    }

    private boolean isRetryable(int error) {
        return error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                || error == SpeechRecognizer.ERROR_CLIENT;
    }

    private void scheduleRestart() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            if (!stopped) {
                if (speechRecognizer != null) {
                    speechRecognizer.destroy();
                    speechRecognizer = null;
                }
                // 재시작 전에도 오디오 포커스를 재요청해 마이크 선점 유지
                audioManager.requestAudioFocus(
                        audioFocusListener,
                        AudioManager.STREAM_VOICE_CALL,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                );
                Log.d(TAG, "Restarting SpeechRecognizer continuousMode = " + continuousMode);
                ensureRecognizer();
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
