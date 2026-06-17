package com.kidsFriend.domain.sensor;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.kidsFriend.global.client.ApiClient;
import com.kidsFriend.global.client.ApiResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * KF_BE의 {@code GET /api/sensor-events/latest} 를 주기적으로 폴링해서
 * 새 이벤트가 도착하면 {@link RobotActionManager}로 넘긴다.
 *
 * <p>같은 이벤트(sensorEventId)에 대해서는 한 번만 반응한다. 폴링 실패는 조용히 무시하고
 * 다음 주기에 재시도하므로, KF_BE가 잠깐 꺼져 있어도 앱은 영향을 받지 않는다.</p>
 *
 * <p>앱 시작 시 DB에 남아 있던 "과거 마지막 이벤트"에 반응하지 않도록,
 * 첫 폴링 결과는 기준선(baseline)으로만 기록하고 행동하지 않는다.</p>
 */
public class SensorEventPoller {
    private static final String TAG = "SensorEventPoller";
    private static final long POLL_INTERVAL_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RobotActionManager actionManager;

    private boolean running = false;
    private String lastHandledId = null;
    private boolean baselined = false;

    // 폴링 루프: 한 번 폴링하고, 동작 중이면 다음 주기를 예약한다.
    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            poll();
            if (running) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    public SensorEventPoller(RobotActionManager actionManager) {
        this.actionManager = actionManager;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        handler.post(pollTask);
        Log.d(TAG, "센서 이벤트 폴링 시작");
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(pollTask);
        Log.d(TAG, "센서 이벤트 폴링 중지");
    }

    private void poll() {
        ApiClient.getService().getLatestSensorEvent().enqueue(
                new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        Map<String, Object> data = response.body().data;
                        if (data == null) {
                            baselined = true; // 이벤트가 아직 없음 = 깨끗한 기준선
                            return;
                        }

                        String id = asString(data.get("sensorEventId"));
                        if (id == null || id.equals(lastHandledId)) {
                            return; // 이미 처리한(또는 식별 불가한) 이벤트
                        }
                        lastHandledId = id;

                        if (!baselined) {
                            baselined = true;
                            Log.d(TAG, "기준선 설정(과거 이벤트 무시): " + id);
                            return;
                        }

                        String eventType = asString(data.get("eventType"));
                        Map<String, Object> payload = asMap(data.get("payload"));

                        Log.d(TAG, "새 센서 이벤트: " + eventType + " " + payload);
                        actionManager.onSensorEvent(eventType, payload);
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                        // 폴링 실패는 조용히 무시하고 다음 주기에 재시도
                    }
                });
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }
}
