package com.kidsFriend.domain.sensor.service;

import android.util.Log;
import com.robotemi.sdk.BatteryData;
import com.robotemi.sdk.Robot;
import com.robotemi.sdk.listeners.OnBatteryStatusChangedListener;
import com.robotemi.sdk.listeners.OnDetectionStateChangedListener;
import com.robotemi.sdk.listeners.OnRobotDragStateChangedListener;
import com.robotemi.sdk.listeners.OnRobotLiftedListener;

import com.kidsFriend.global.voice.TemiSpeechSpeaker;

/**
 * 시연 중 돌발상황을 <b>Temi 내장 기능</b>으로 자동 복구하는 레이어.
 *
 * <ul>
 *   <li><b>내장 사람감지(detection mode)</b>: KF_BE/KF_HW/ngrok가 모두 죽어도 테미 자체 카메라로
 *       아이 접근을 감지해 인사 반응을 한다. (센서 경로의 백업 — 데모 안전망의 핵심)</li>
 *   <li><b>들림/끌림 감지</b>: 누가 로봇을 들거나 밀면 즉시 정지하고 안내한다.</li>
 *   <li><b>배터리 부족</b>: 음성으로 안내한다. (충전 도크 자동 복귀는 자율주행이라 시연 안전상 기본 비활성)</li>
 * </ul>
 *
 * 모든 Temi SDK 호출은 로봇이 없는 환경(에뮬레이터/일반 폰)에서도 앱이 죽지 않도록 try/catch로 감싼다.
 * 실제 반응(발화·표정·고개)은 {@link RobotActionManager}를 재사용한다.
 */
public class RobotResilienceManager implements
        OnDetectionStateChangedListener,
        OnRobotLiftedListener,
        OnRobotDragStateChangedListener,
        OnBatteryStatusChangedListener {

    private static final String TAG = "RobotResilience";

    /** 내장 사람감지 최대 거리(m). 허용 0.5~2.0. */
    private static final float DETECTION_DISTANCE_M = 1.5f;
    private static final int LOW_BATTERY_PCT = 15;
    private static final long BATTERY_WARN_INTERVAL_MS = 60_000L;

    private final RobotActionManager actionManager;
    private final TemiSpeechSpeaker speaker = new TemiSpeechSpeaker();

    private boolean registered = false;
    private long lastBatteryWarnAt = 0L;

    public RobotResilienceManager(RobotActionManager actionManager) {
        this.actionManager = actionManager;
    }

    /** 로봇이 준비된 뒤(onRobotReady) 호출. 내장 리스너 등록 + 사람감지 모드 ON. (중복 호출 안전) */
    public void register() {
        if (registered) {
            return;
        }
        try {
            Robot robot = Robot.getInstance();
            robot.addOnDetectionStateChangedListener(this);
            robot.addOnRobotLiftedListener(this);
            robot.addOnRobotDragStateChangedListener(this);
            robot.addOnBatteryStatusChangedListener(this);
            robot.setDetectionModeOn(true, DETECTION_DISTANCE_M); // 내장 사람감지 ON (백업 센서)
            registered = true;
            Log.d(TAG, "Temi 내장 복구 리스너 등록 + 사람감지 모드 ON");
        } catch (RuntimeException e) {
            Log.w(TAG, "복구 리스너 등록 실패(로봇 환경이 아닐 수 있음): " + e.getMessage());
        }
    }

    /** 화면 종료 시 호출. 리스너 해제 + 감지 모드 OFF. */
    public void unregister() {
        if (!registered) {
            return;
        }
        try {
            Robot robot = Robot.getInstance();
            robot.removeOnDetectionStateChangedListener(this);
            robot.removeOnRobotLiftedListener(this);
            robot.removeOnRobotDragStateChangedListener(this);
            robot.removeOnBatteryStatusChangedListener(this);
            robot.setDetectionModeOn(false, DETECTION_DISTANCE_M);
        } catch (RuntimeException e) {
            Log.w(TAG, "복구 리스너 해제 실패: " + e.getMessage());
        }
        registered = false;
    }

    // === 내장 사람감지: 백엔드/HW가 죽어도 테미 자체 카메라로 아이 접근에 반응 (백업 경로) ===
    @Override
    public void onDetectionStateChanged(int state) {
        if (state == OnDetectionStateChangedListener.DETECTED) {
            Log.d(TAG, "내장 감지: 사람 DETECTED → 인사 반응(백업 트리거)");
            actionManager.onSensorEvent("CHILD_DETECTED", null);
        }
    }

    // === 누가 로봇을 들면: 즉시 정지 + 안내 ===
    @Override
    public void onRobotLifted(boolean isLifted, String reason) {
        if (isLifted) {
            Log.d(TAG, "로봇 들림 감지: " + reason);
            safeStop();
            speaker.speak("앗, 들렸어요! 천천히 내려주세요.");
        }
    }

    // === 누가 로봇을 밀면: 즉시 정지 + 안내 ===
    @Override
    public void onRobotDragStateChanged(boolean isDragged) {
        if (isDragged) {
            Log.d(TAG, "로봇 끌림 감지");
            safeStop();
            speaker.speak("같이 가요? 살살 밀어주세요!");
        }
    }

    // === 배터리 부족: 음성 안내(과도한 반복 방지) ===
    @Override
    public void onBatteryStatusChanged(BatteryData batteryData) {
        if (batteryData == null || batteryData.isCharging()) {
            return;
        }
        if (batteryData.getBatteryPercentage() > LOW_BATTERY_PCT) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBatteryWarnAt < BATTERY_WARN_INTERVAL_MS) {
            return;
        }
        lastBatteryWarnAt = now;
        speaker.speak("배터리가 부족해요. 곧 충전이 필요해요!");
        // 충전 도크 자동 복귀를 원하면: Robot.getInstance().goTo("home base")
        // (자율주행이라 시연 안전상 기본 비활성)
    }

    private void safeStop() {
        try {
            Robot.getInstance().stopMovement();
        } catch (RuntimeException e) {
            Log.w(TAG, "stopMovement 실패: " + e.getMessage());
        }
    }
}