package com.kidsFriend.global.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.kidsFriend.R;

/**
 * 음성 인식 오케스트레이터.
 *
 * <p>두 엔진을 상황에 맞게 고른다.
 * <ul>
 *   <li><b>연속(웨이크 대기)</b> → {@link TemiSttEngine}. 소음에 강한 테미로 "친구야"를 안정적으로 잡는다.</li>
 *   <li><b>단일(대화 듣기)</b> → {@link AndroidSttEngine} 우선. 부분결과로 실시간 자막을 만들되,
 *       워치독으로 무응답/데드락을 감지하면 {@link TemiSttEngine}으로 투명하게 폴백한다.</li>
 * </ul>
 *
 * <p>공개 API(시작/정지/해제/가용성)는 기존과 동일해 호출부 수정이 없다. 죽어 있던
 * {@link Callback#onPartialResult(String)}가 이제 SpeechRecognizer 부분결과로 실제 호출된다.
 */
public class VoiceInputManager {
    private static final String TAG = "VoiceInputManager";

    /** 마지막 신호(준비/부분결과) 이후 이 시간 동안 진전 없으면 테미로 폴백. */
    private static final long WATCHDOG_MS = 4000;

    // SpeechRecognizer 에러 코드 중 "무발화/타임아웃"(사용자가 말 안 함). 폴백 대신 그대로 알린다.
    private static final int ERROR_SPEECH_TIMEOUT = 6;
    private static final int ERROR_NO_MATCH = 7;

    public interface Callback {
        void onReady();
        void onPartialResult(String text);
        void onResult(String text);
        void onError(String message);
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TemiSttEngine temiEngine = new TemiSttEngine();
    private final AndroidSttEngine androidEngine;
    private final boolean androidAvailable;

    private SttEngine activeEngine;
    private Runnable watchdog;
    private boolean fellBack;

    public VoiceInputManager(Context context) {
        this.context = context.getApplicationContext();
        this.androidEngine = new AndroidSttEngine(this.context);
        this.androidAvailable = AndroidSttEngine.isAvailable(this.context);
        Log.d(TAG, "SpeechRecognizer available = " + androidAvailable);
    }

    /** 테미 STT가 항상 백업으로 있으므로 인식은 항상 가능하다고 본다. */
    public static boolean isRecognitionAvailable(Context context) {
        return true;
    }

    /** 한 발화만 듣는다(대화). SpeechRecognizer 우선 + 테미 폴백. */
    public void startSingleListening(Callback callback) {
        stopListening();
        fellBack = false;
        if (androidAvailable) {
            startAndroidWithFallback(callback);
        } else {
            startTemiSingle(callback);
        }
    }

    /** 계속 듣는다(웨이크 대기). "테미 내장 UI를 버리고 우리 것(Android)"으로 진행. */
    public void startContinuousListening(Callback callback) {
        stopListening();
        if (androidAvailable) {
            activeEngine = androidEngine;
            androidEngine.start(true, callback);
        } else {
            // Android STT 불가 시에만 테미로 폴백
            activeEngine = temiEngine;
            temiEngine.start(true, callback);
        }
    }

    public void stopListening() {
        cancelWatchdog();
        if (activeEngine != null) {
            activeEngine.stop();
            activeEngine = null;
        }
    }

    public void destroy() {
        cancelWatchdog();
        androidEngine.destroy();
        temiEngine.destroy();
        activeEngine = null;
    }

    // ── 단일 듣기: SpeechRecognizer 우선 + 폴백 ───────────────────────────────

    private void startAndroidWithFallback(Callback userCallback) {
        activeEngine = androidEngine;
        armWatchdog(userCallback);
        androidEngine.start(false, new Callback() {
            @Override
            public void onReady() {
                resetWatchdog(userCallback); // 엔진 응답 → 진전으로 간주
                userCallback.onReady();
            }

            @Override
            public void onPartialResult(String text) {
                // 발화가 한 번 들리면 엔진은 살아있는 게 확실하다. 이후 확정은 우리(AndroidSttEngine)의
                // 1초 침묵 감지가 책임지므로, 테미 폴백 워치독을 꺼서 테미가 우리 기능을 가로채지 못하게 한다.
                cancelWatchdog();
                userCallback.onPartialResult(text);
            }

            @Override
            public void onResult(String text) {
                cancelWatchdog();
                userCallback.onResult(text);
            }

            @Override
            public void onError(String message) {
                cancelWatchdog();
                handleAndroidError(message, userCallback);
            }
        });
    }

    private void handleAndroidError(String message, Callback userCallback) {
        int code = parseCode(message);
        // 사용자가 말을 안 한 경우(무발화/타임아웃)는 폴백해도 똑같이 빈 결과 → 그대로 알린다.
        if (code == ERROR_SPEECH_TIMEOUT || code == ERROR_NO_MATCH) {
            userCallback.onError(context.getString(R.string.voice_not_recognized));
            return;
        }
        // 그 외(busy/audio/server/client/network 등)는 엔진 문제 → 테미로 투명 폴백.
        fallbackToTemi(userCallback);
    }

    private void fallbackToTemi(Callback userCallback) {
        if (fellBack) {
            return;
        }
        fellBack = true;
        Log.w(TAG, "SpeechRecognizer 실패/무응답 → 테미 STT로 폴백.");
        androidEngine.stop();
        startTemiSingle(userCallback);
    }

    private void startTemiSingle(Callback userCallback) {
        activeEngine = temiEngine;
        temiEngine.start(false, userCallback);
    }

    // ── 워치독: 무응답/데드락 감지 ────────────────────────────────────────────

    private void armWatchdog(Callback userCallback) {
        cancelWatchdog();
        watchdog = () -> {
            Log.w(TAG, "SpeechRecognizer 진전 없음(" + WATCHDOG_MS + "ms) → 테미 폴백.");
            fallbackToTemi(userCallback);
        };
        handler.postDelayed(watchdog, WATCHDOG_MS);
    }

    private void resetWatchdog(Callback userCallback) {
        if (fellBack) {
            return;
        }
        armWatchdog(userCallback);
    }

    private void cancelWatchdog() {
        if (watchdog != null) {
            handler.removeCallbacks(watchdog);
            watchdog = null;
        }
    }

    private int parseCode(String message) {
        if (message == null) {
            return -1;
        }
        int idx = message.lastIndexOf('_');
        if (idx < 0 || idx + 1 >= message.length()) {
            return -1;
        }
        try {
            return Integer.parseInt(message.substring(idx + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}