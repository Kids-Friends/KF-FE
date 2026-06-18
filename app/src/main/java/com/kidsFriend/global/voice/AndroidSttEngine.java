package com.kidsFriend.global.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

/**
 * 안드로이드 {@link SpeechRecognizer} 기반 음성 인식 엔진.
 *
 * <p>핵심 가치는 {@code onPartialResults}로 들어오는 <b>부분(interim) 결과</b>다. 발화 도중 단어가
 * 점점 쌓이며 갱신되므로 GenieTV식 실시간 자막을 만들 수 있다. 단, 행사장 소음에서 {@code onResults}가
 * 영구 미발생하는 데드락 위험(KNOWN_RISKS P0)이 있어, 상위 {@link VoiceInputManager}가 워치독으로
 * 무응답을 감지하면 테미 인식으로 폴백한다.
 *
 * <p>에러는 {@code callback.onError("STT_ERROR_<code>")} 형식으로 코드를 실어 보내, 폴백 여부를
 * {@link VoiceInputManager}가 코드로 판단하게 한다.
 */
public class AndroidSttEngine implements SttEngine, RecognitionListener {
    private static final String TAG = "AndroidSttEngine";

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer recognizer;
    private VoiceInputManager.Callback callback;
    private boolean stopped = true;

    public AndroidSttEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 이 기기에서 SpeechRecognizer를 쓸 수 있는지. false면 처음부터 테미로 가야 한다. */
    public static boolean isAvailable(Context context) {
        try {
            return SpeechRecognizer.isRecognitionAvailable(context);
        } catch (Exception e) {
            Log.e(TAG, "isAvailable exception", e);
            return false;
        }
    }

    @Override
    public void start(boolean continuous, VoiceInputManager.Callback callback) {
        // continuous는 안드로이드 SR(단발 인식)엔 의미 없음(대화 듣기 = 한 발화). 무시한다.
        this.callback = callback;
        this.stopped = false;
        handler.post(() -> {
            try {
                if (recognizer == null) {
                    recognizer = SpeechRecognizer.createSpeechRecognizer(context);
                    recognizer.setRecognitionListener(this);
                }
                recognizer.startListening(buildIntent());
            } catch (Exception e) {
                Log.e(TAG, "start exception", e);
                if (!stopped && callback != null) {
                    callback.onError("STT_ERROR_5"); // ERROR_CLIENT 취급 → 폴백 유도
                }
            }
        });
    }

    private Intent buildIntent() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        return intent;
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        handler.post(() -> {
            if (recognizer != null) {
                try {
                    recognizer.cancel();
                } catch (Exception e) {
                    Log.e(TAG, "stop exception", e);
                }
            }
        });
    }

    @Override
    public void destroy() {
        stopped = true;
        handler.post(() -> {
            if (recognizer != null) {
                try {
                    recognizer.destroy();
                } catch (Exception e) {
                    Log.e(TAG, "destroy exception", e);
                }
                recognizer = null;
            }
        });
    }

    // ── RecognitionListener ──────────────────────────────────────────────────

    @Override
    public void onReadyForSpeech(Bundle params) {
        // 엔진이 응답함(살아있음 신호) → "듣는 중" 표시.
        if (!stopped && callback != null) {
            callback.onReady();
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (stopped || callback == null) {
            return;
        }
        String text = first(partialResults);
        if (text != null && !text.isEmpty()) {
            callback.onPartialResult(text); // 단어별 실시간 자막
        }
    }

    @Override
    public void onResults(Bundle results) {
        if (stopped || callback == null) {
            return;
        }
        stopped = true;
        String text = first(results);
        callback.onResult(text != null ? text : "");
    }

    @Override
    public void onError(int error) {
        if (stopped || callback == null) {
            return;
        }
        stopped = true;
        Log.w(TAG, "onError: code = " + error);
        callback.onError("STT_ERROR_" + error);
    }

    @Override
    public void onBeginningOfSpeech() {
    }

    @Override
    public void onRmsChanged(float rmsdB) {
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
    }

    @Override
    public void onEndOfSpeech() {
    }

    @Override
    public void onEvent(int eventType, Bundle params) {
    }

    private String first(Bundle bundle) {
        if (bundle == null) {
            return null;
        }
        ArrayList<String> list = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0).trim();
    }
}
