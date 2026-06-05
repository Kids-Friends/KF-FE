package com.kidsFriend.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.robotemi.sdk.NlpResult;
import com.robotemi.sdk.Robot;

/**
 * 안드로이드 표준 SpeechRecognizer 대신 테미 SDK의 음성 인식 기능을 사용하는 매니저입니다.
 * 테미 하드웨어와 내장 STT 엔진을 사용하여 안정적인 음성 인식을 제공합니다.
 */
public class VoiceInputManager implements Robot.NlpListener {
    private static final String TAG = "VoiceInputManager";

    public interface Callback {
        void onReady();
        void onPartialResult(String text);
        void onResult(String text);
        void onError(String message);
    }

    private final Robot robot;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Callback callback;
    private boolean continuousMode;
    private boolean stopped = true;

    public VoiceInputManager(Context context) {
        this.robot = Robot.getInstance();
    }

    /**
     * 테미 로봇 환경에서는 테미 SDK를 통한 음성 인식이 항상 가능하다고 간주합니다.
     */
    public static boolean isRecognitionAvailable(Context context) {
        return true;
    }

    public void startSingleListening(Callback callback) {
        startListening(false, callback);
    }

    public void startContinuousListening(Callback callback) {
        startListening(true, callback);
    }

    public void stopListening() {
        if (stopped) return;
        
        stopped = true;
        continuousMode = false;
        handler.removeCallbacksAndMessages(null);
        robot.removeNlpListener(this);
        robot.finishConversation();
        Log.d(TAG, "stopListening: Voice recognition stopped.");
    }

    public void destroy() {
        stopListening();
    }

    private void startListening(boolean continuousMode, Callback callback) {
        this.continuousMode = continuousMode;
        this.callback = callback;
        this.stopped = false;

        Log.d(TAG, "startListening: continuousMode = " + continuousMode);
        
        // 기존 리스너가 있다면 제거 후 새로 등록 (중복 방지)
        robot.removeNlpListener(this);
        robot.addNlpListener(this);
        
        // 테미 전용 STT 시작 (음성 인식 UI 활성화)
        // 안내 멘트 없이 바로 듣기 시작 (발화 지연/자기음성 간섭 제거)
        robot.askQuestion("");
        
        if (callback != null) {
            callback.onReady();
        }
    }

    private int rapidFailCount = 0;
    private long lastNlpTime = 0;

    @Override
    public void onNlpCompleted(NlpResult nlpResult) {
        if (stopped || callback == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastNlpTime < 1000) {
            rapidFailCount++;
        } else {
            rapidFailCount = 0;
        }
        lastNlpTime = now;

        // [방어 코드] 1초 이내에 실패가 5번 이상 반복되면 하드웨어/마이크 고장으로 간주하고 연속 듣기를 강제 중단 (ANR 방지)
        if (rapidFailCount > 5) {
            Log.e(TAG, "onNlpCompleted: Circuit Breaker triggered! STT hardware might be broken. Stopping infinite loop.");
            stopListening();
            return;
        }

        String text = (nlpResult != null && nlpResult.resolvedQuery != null) 
                ? nlpResult.resolvedQuery.trim() : "";
        
        Log.d(TAG, "onNlpCompleted: text = [" + text + "]");

        if (!text.isEmpty()) {
            callback.onResult(text);
        } else {
            // 인식이 되지 않았거나 취소된 경우
            Log.d(TAG, "onNlpCompleted: No speech recognized or cancelled.");
        }

        if (continuousMode && !stopped) {
            // 지속 모드인 경우 지연 후 다시 인식 시작 (무한 루프 방지를 위해 최소 1초 대기)
            handler.postDelayed(() -> {
                if (!stopped) {
                    Log.d(TAG, "Restarting askQuestion for continuous mode.");
                    robot.askQuestion(""); 
                }
            }, 1000);
        } else if (!continuousMode) {
            stopListening();
        }
    }
}
