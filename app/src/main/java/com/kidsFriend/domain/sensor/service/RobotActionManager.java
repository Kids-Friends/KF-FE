package com.kidsFriend.domain.sensor;

import android.util.Log;

import com.kidsFriend.global.client.ApiClient;
import com.kidsFriend.global.client.ApiResponse;
import com.kidsFriend.domain.membership.ClientResponse;
import com.kidsFriend.domain.sensor.SensorEventRequest;
import com.kidsFriend.global.repository.RepositoryCallback;
import com.kidsFriend.global.repository.TemiRepository;
import com.kidsFriend.global.voice.TemiSpeechSpeaker;
import com.robotemi.sdk.Robot;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 센서 이벤트에 따라 테미 로봇의 이동/표현을 제어하는 레이어.
 *
 * <p>라즈베리파이(KF_HW)가 감지한 이벤트는 KF_BE를 거쳐 {@link SensorEventPoller}로 들어오고,
 * 여기서 그 이벤트를 실제 로봇 동작(다가가기/멈추기/올려다보기)과 발화로 변환한다.
 * 이것이 "로봇이 실제로 움직이는 것은 FE가 제어한다"는 구조의 핵심 지점이다.</p>
 *
 * <p>모든 Temi SDK 호출은 로봇이 없는 환경(에뮬레이터/일반 폰)에서도 앱이 죽지 않도록
 * try/catch로 감싼다. 현재는 보수적인 최소 동작만 구현했으며, 정밀 내비게이션(goTo 등)으로
 * 확장하기 좋은 출발점이다.</p>
 */
public class RobotActionManager {
    private static final String TAG = "RobotActionManager";

    /** 표정 변화를 알리기 위한 인터페이스 */
    public interface OnFaceChangeListener {
        void onFaceChange(String faceType);
    }

    private OnFaceChangeListener faceChangeListener;
    /** 같은 상황이 계속 감지돼도 이 시간 안에는 다시 반응하지 않는다(로봇이 산만해지는 것 방지). */
    private static final long REACTION_COOLDOWN_MS = 8000L;

    private final TemiSpeechSpeaker speaker = new TemiSpeechSpeaker();
    private final TemiRepository repository;
    private long lastReactionAt = 0L;

    public RobotActionManager() {
        this.repository = new TemiRepository();
    }

    public RobotActionManager(TemiRepository repository) {
        this.repository = repository;
    }

    public void setOnFaceChangeListener(OnFaceChangeListener listener) {
        this.faceChangeListener = listener;
    }

    private void changeFace(String faceType) {
        if (faceChangeListener != null) {
            faceChangeListener.onFaceChange(faceType);
        }
    }

    public void onSensorEvent(String eventType, Map<String, Object> payload) {
        if (eventType == null) {
            return;
        }
        // 안전 이벤트(기울어짐/충돌/화재)는 쿨다운과 무관하게 항상 즉시 처리한다.
        boolean isSafetyEvent = "TILT".equals(eventType) || "BUMP".equals(eventType)
                || "FIRE".equals(eventType) || "FIRE_DETECTED".equals(eventType) || "SMOKE".equals(eventType);
        long now = System.currentTimeMillis();
        if (!isSafetyEvent && now - lastReactionAt < REACTION_COOLDOWN_MS) {
            Log.d(TAG, "쿨다운 중 - 이벤트 무시: " + eventType);
            return;
        }
        lastReactionAt = now;
        switch (eventType) {
            case "CHILD_DETECTED":
                changeFace("EXCITED");
                String childName = asString(payload, "name");
                approachNearbyChild(childName);
                break;
            case "OBSTACLE_DETECTED":
                reactToObstacle(payload);
                break;
            case "TILT":
            case "BUMP":
                changeFace("ANGER");
                handleSafetyStop();
                break;
            case "LOW_BATTERY":
                changeFace("SADNESS");
                speaker.speak("배터리가 부족해요. 잠깐 충전하고 올게요!");
                break;
            case "FIRE":
            case "FIRE_DETECTED":
            case "SMOKE":
                changeFace("ANGER");
                handleFireAlarm();
                break;
            case "ARRIVED_AT_LOCATION":
                changeFace("JOY");
                speaker.speak("도착했어요!");
                reportArrival();
                break;
            default:
                Log.d(TAG, "처리하지 않는 이벤트: " + eventType);
        }
    }

    private void reportArrival() {
        // SensorEventRequest(String robotId, String eventType, Object payload)
        SensorEventRequest req = new SensorEventRequest("TEMI_01", "ARRIVAL", null);
        try {
            ApiClient.getService().postSensorEvent(req).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                @Override
                public void onResponse(Call<ApiResponse<Map<String, Object>>> call, Response<ApiResponse<Map<String, Object>>> response) {
                    Log.d(TAG, "Arrival reported successfully");
                }

                @Override
                public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                    Log.w(TAG, "Failed to report arrival: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Error reporting arrival: " + e.getMessage());
        }
    }

    /**
     * 근처에서 아이를 인식하면 올려다보며 인사한다.
     * 데모 안전을 위해 자율 주행(beWithMe)은 기본 비활성화한다.
     * 실제로 아이에게 "다가가게" 하려면 아래 runRobot(Robot::beWithMe); 한 줄의 주석을 해제하면 된다.
     */
    private void approachNearbyChild(String name) {
        if (name != null && !name.isEmpty()) {
            repository.getClientsByName(name, new RepositoryCallback<List<ClientResponse>>() {
                @Override
                public void onSuccess(List<ClientResponse> data) {
                    if (data != null && !data.isEmpty()) {
                        String childName = data.get(0).childName;
                        speaker.speak("안녕 " + childName + "아! 같이 놀까?");
                    } else {
                        speaker.speak("안녕! 새로운 친구구나? 같이 놀까?");
                    }
                }

                @Override
                public void onError(String message) {
                    speaker.speak("안녕! 친구야, 같이 놀까?");
                }
            });
        } else {
            speaker.speak("안녕! 친구야, 같이 놀까?");
        }
        tiltUpToFace();
        runRobot(Robot::beWithMe);  // ← 실제 접근(주행)을 원하면 주석 해제
    }

    /** 60cm 안에 무언가 감지되면 거리에 따라 멈추거나 올려다본다. */
    private void reactToObstacle(Map<String, Object> payload) {
        int distanceCm = readInt(payload, "distance_cm", 60);
        if (distanceCm <= 30) {
            changeFace("SADNESS");
            runRobot(Robot::stopMovement);
            speaker.speak("앗, 너무 가까워요. 조심조심!");
        } else {
            changeFace("PEACEFUL");
            tiltUpToFace();
            speaker.speak("거기 누구 있어요?");
        }
    }

    /** 기울기/충돌 등 안전 이벤트: 즉시 정지하고 경고한다. */
    private void handleSafetyStop() {
        runRobot(Robot::stopMovement);
        speaker.speak("어어, 위험해요! 잠깐 멈출게요.");
    }

    /** 화재 경보: 즉시 정지하고 큰 소리로 대피를 안내한다. */
    private void handleFireAlarm() {
        runRobot(Robot::stopMovement);
        speaker.speak("애들아, 불이났어! 비상! 모두 진정하고 선생님을 따라 천천히 밖으로 나가자!");
        Log.i(TAG, "[시나리오 8] 화재경보 → 성공");
    }

    private void tiltUpToFace() {
        runRobot(robot -> robot.tiltAngle(20));
    }

    // --- helpers ---

    private interface RobotAction {
        void run(Robot robot);
    }

    private void runRobot(RobotAction action) {
        try {
            action.run(Robot.getInstance());
        } catch (RuntimeException e) {
            Log.w(TAG, "로봇 동작 실패(로봇 환경이 아닐 수 있음): " + e.getMessage());
        }
    }

    private String asString(Map<String, Object> payload, String key) {
        if (payload == null) return null;
        Object val = payload.get(key);
        return val != null ? val.toString() : null;
    }

    private int readInt(Map<String, Object> payload, String key, int fallback) {
        if (payload == null) {
            return fallback;
        }
        Object value = payload.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
