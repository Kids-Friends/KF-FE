package com.kidsFriend.global.voice;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.robotemi.sdk.Robot;

/**
 * 테미 SDK 음성 인식 엔진.
 *
 * <p>테미 하드웨어/내장 STT를 쓰므로 행사장 소음에 강하다. 단, 부분(interim) 결과는 제공하지 않아
 * {@link VoiceInputManager.Callback#onPartialResult(String)}는 호출하지 않고 최종 결과만 준다.
 * (기존 {@code VoiceInputManager}의 테미 로직을 그대로 이전 — 서킷브레이커/연속모드 재시작 포함.)
 */
public class TemiSttEngine implements SttEngine, Robot.AsrListener {
    private static final String TAG = "TemiSttEngine";

    private final Robot robot = Robot.getInstance();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private VoiceInputManager.Callback callback;
    private boolean continuousMode;
    private boolean stopped = true;

    private int rapidFailCount = 0;
    private long lastNlpTime = 0;

    @Override
    public void start(boolean continuousMode, VoiceInputManager.Callback callback) {
        this.continuousMode = continuousMode;
        this.callback = callback;
        this.stopped = false;
        // 새로 시작할 때 서킷브레이커 상태를 초기화한다(이전 실패 누적으로 즉시 멈추는 것 방지).
        this.rapidFailCount = 0;
        this.lastNlpTime = 0;

        Log.d(TAG, "start: continuousMode = " + continuousMode);

        // 기존 리스너가 있다면 제거 후 새로 등록 (중복 방지)
        // AsrListener: 테미 ASR raw 결과를 onAsrResult 로 직접 받는다(NLP 의도분석을 거치지 않아 발화 누락이 적음).
        robot.removeAsrListener(this);
        robot.addAsrListener(this);

        // 테미 전용 STT 시작 (음성 인식 UI 활성화)
        // 안내 멘트 없이 바로 듣기 시작 (발화 지연/자기음성 간섭 제거)
        // 마이크 성능을 고려하여 로봇의 시각적 피드백(파란색 바)이 보이도록 설정
        robot.askQuestion("듣고 있어요");

        if (callback != null) {
            callback.onReady();
        }
    }

    @Override
    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        continuousMode = false;
        handler.removeCallbacksAndMessages(null);
        robot.removeAsrListener(this);
        robot.finishConversation();
        Log.d(TAG, "stop: Voice recognition stopped.");
    }

    @Override
    public void destroy() {
        stop();
    }

    @Override
    public void onAsrResult(String asrResult) {
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

        // [방어 코드] 1초 이내에 실패가 5번 이상 반복되면 하드웨어/마이크 고장으로 간주하고 폭주 루프를 멈춘다.
        // 단, 영구 정지하지 않는다: 사용자에게 터치를 안내한 뒤 잠시 후 자동으로 다시 듣기를 시도한다.
        if (rapidFailCount > 5) {
            Log.e(TAG, "onAsrResult: Circuit Breaker! STT 폭주 → 일시 중단 후 자동 복구 예약.");
            final VoiceInputManager.Callback savedCallback = this.callback;
            final boolean wasContinuous = this.continuousMode;
            stop();
            if (savedCallback != null) {
                savedCallback.onError("마이크가 잘 안 들려요. 화면을 눌러줘!");
            }
            if (wasContinuous && savedCallback != null) {
                // 5초 뒤 연속 듣기를 자동 재시작한다(마이크/네트워크가 잠깐 꼬여도 스스로 회복).
                handler.postDelayed(() -> {
                    if (stopped) {
                        start(true, savedCallback);
                    }
                }, 5000);
            }
            return;
        }

        String text = (asrResult != null) ? asrResult.trim() : "";

        Log.d(TAG, "onAsrResult: text = [" + text + "]");

        if (!text.isEmpty()) {
            callback.onResult(text);
        } else {
            // 인식이 되지 않았거나 취소된 경우: 대화를 끝내지 않고 빈 결과를 전달해 
            // 호출부(MainActivity 등)에서 '잘 못 들었어요' 처리를 하게 함.
            Log.d(TAG, "onAsrResult: No speech recognized or cancelled. Returning empty.");
            callback.onResult("");
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
            stop();
        }
    }
}