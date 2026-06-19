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

    /** 부분결과가 들어온 뒤 이 시간 동안 새 발화가 없으면 그 텍스트로 최종 확정한다(발화 끝 침묵 감지). */
    private static final long SILENCE_TIMEOUT_MS = 1000;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable silenceFinalizer = this::finalizeFromSilence;

    private SpeechRecognizer recognizer;
    private VoiceInputManager.Callback callback;
    private boolean stopped = true;
    private String lastPartialText = "";

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
        this.lastPartialText = "";
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
        handler.removeCallbacks(silenceFinalizer);
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
        handler.removeCallbacks(silenceFinalizer);
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
            lastPartialText = text;
            callback.onPartialResult(text); // 단어별 실시간 자막
            armSilenceFinalizer(); // 발화 후 1초 침묵하면 이 텍스트로 답변 확정
        }
    }

    /** 부분결과 수신 후 침묵 타이머를 (재)시작한다. 새 발화가 오면 onPartialResults에서 다시 갱신된다. */
    private void armSilenceFinalizer() {
        handler.removeCallbacks(silenceFinalizer);
        handler.postDelayed(silenceFinalizer, SILENCE_TIMEOUT_MS);
    }

    /**
     * 발화 끝 침묵 감지: 마지막 부분결과를 최종 결과로 확정한다.
     *
     * <p>테미/SpeechRecognizer의 자체 onResults를 기다리지 않고 우리가 먼저 확정하므로,
     * 소음으로 onResults가 영구 미발생하는 데드락에서도 답변으로 넘어간다.
     */
    private void finalizeFromSilence() {
        if (stopped || callback == null || lastPartialText.isEmpty()) {
            return;
        }
        stopped = true;
        try {
            if (recognizer != null) {
                recognizer.cancel();
            }
        } catch (Exception e) {
            Log.e(TAG, "finalizeFromSilence cancel exception", e);
        }
        Log.d(TAG, "finalizeFromSilence: 1초 침묵 → 부분결과로 확정 [" + lastPartialText + "]");
        callback.onResult(lastPartialText);
    }

    @Override
    public void onResults(Bundle results) {
        if (stopped || callback == null) {
            return;
        }
        stopped = true;
        handler.removeCallbacks(silenceFinalizer);
        String text = first(results);
        callback.onResult(text != null ? text : "");
    }

    @Override
    public void onError(int error) {
        if (stopped || callback == null) {
            return;
        }
        stopped = true;
        handler.removeCallbacks(silenceFinalizer);
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