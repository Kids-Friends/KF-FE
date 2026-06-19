package com.kidsFriend.domain.sensor.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.kidsFriend.global.config.AppConfig;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * BE(/ws/sensors)에서 센서 이벤트를 수신해 로봇 동작으로 라우팅하는 WebSocket 클라이언트.
 *
 * <p>HW → BE(POST /api/sensor-events) → 이 클라이언트(WS) → {@link RobotActionManager#onSensorEvent}
 * 흐름의 FE 종단이다. 공기질(AIR_QUALITY)은 1초 주기라 화면을 매번 갱신하지 않고 최신 pm25만
 * 캐시해 {@link #airQualityGradeOrNull()}로 노출한다(시나리오 2.6). 화재(FIRE_ALARM)는
 * status=DETECTED 일 때 즉시 발동한다(2.8).</p>
 */
public class SensorWebSocketClient {
    private static final String TAG = "SensorWsClient";
    private static final long RECONNECT_DELAY_MS = 10000L; // 10초로 연장
    private static final long AIR_QUALITY_TTL_MS = 30000L; // 30초 지난 값은 무효 → "보통" 폴백

    // 공기질 최신값 캐시(휘발성). TemiRepository.getAirQuality 에서 읽는다.
    private static volatile Double latestPm25 = null;
    private static volatile long latestPm25At = 0L;

    /** 최신 pm25 → 등급(좋음/보통/나쁨). 값이 없거나 오래되면 null(폴백 신호). 환경부 PM2.5 기준. */
    public static String airQualityGradeOrNull() {
        Double pm = latestPm25;
        if (pm == null || System.currentTimeMillis() - latestPm25At > AIR_QUALITY_TTL_MS) {
            return null;
        }
        if (pm <= 15) return "좋음";
        if (pm <= 35) return "보통";
        return "나쁨";
    }

    private final RobotActionManager actionManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OkHttpClient client;
    private WebSocket webSocket;
    private volatile boolean closed = false;

    public SensorWebSocketClient(RobotActionManager actionManager) {
        this.actionManager = actionManager;
    }

    /** WS 연결을 시작한다. 로봇/네트워크가 없어도 앱이 죽지 않도록 실패는 재연결로 흡수한다. */
    public void connect() {
        closed = false;
        if (webSocket != null) {
            return; // 이미 연결됨(중복 connect 방지)
        }
        if (client == null) {
            client = new OkHttpClient.Builder()
                    .pingInterval(20, TimeUnit.SECONDS)
                    .build();
        }
        String url = wsUrl();
        Log.i(TAG, "WS 연결 시도: " + url);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("ngrok-skip-browser-warning", "true")
                .build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.i(TAG, "WS 연결됨: /ws/sensors");
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleMessage(text);
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                // SSL 인증서 오류 등 반복적인 실패 시 로그 도배 방지
                if (t != null && t.getMessage() != null && t.getMessage().contains("Trust anchor")) {
                    Log.w(TAG, "WS 연결 실패 (SSL/인증서 문제 - 백엔드 HTTPS 설정 확인 필요)");
                } else {
                    Log.w(TAG, "WS 실패: " + (t == null ? "" : t.getMessage()));
                }
                webSocket = null;
                scheduleReconnect();
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.i(TAG, "WS 종료(" + code + "): " + reason);
                webSocket = null;
                if (!closed) scheduleReconnect();
            }
        });
    }

    public void disconnect() {
        closed = true;
        if (webSocket != null) {
            webSocket.close(1000, "bye");
            webSocket = null;
        }
    }

    private void scheduleReconnect() {
        if (closed) return;
        mainHandler.postDelayed(() -> { if (!closed) connect(); }, RECONNECT_DELAY_MS);
    }

    /** BE base URL(https/http)에서 ws 주소를 만든다. 예: https://x/ → wss://x/ws/sensors */
    private String wsUrl() {
        String base;
        try {
            base = AppConfig.getInstance().getBaseUrl(); // 예: https://...ngrok-free.dev/
        } catch (Exception e) {
            base = "https://avengeful-shaunte-revolvingly.ngrok-free.dev/"; // AppConfig 미초기화 폴백
        }
        String ws = base.replaceFirst("^http", "ws");       // https→wss, http→ws
        if (!ws.endsWith("/")) ws += "/";
        return ws + "ws/sensors";
    }

    private void handleMessage(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            String eventType = obj.optString("eventType", "");
            JSONObject payload = obj.optJSONObject("payload");

            // 공기질: pm25 최신값만 캐시(화면 갱신 없음 — 1초 주기 폭주 방지)
            if (payload != null && payload.has("pm25")) {
                latestPm25 = payload.optDouble("pm25");
                latestPm25At = System.currentTimeMillis();
            }
            if ("AIR_QUALITY".equals(eventType) || "DUST_HIGH".equals(eventType)) {
                return; // 캐시만 하고 즉시 동작은 없음
            }

            // 화재: DETECTED 일 때만 발동(CLEARED 무시)
            if ("FIRE_ALARM".equals(eventType)) {
                String status = payload != null ? payload.optString("status", "") : "";
                if (!"DETECTED".equals(status)) {
                    return;
                }
            }

            Map<String, Object> payloadMap = toMap(payload);
            mainHandler.post(() -> actionManager.onSensorEvent(eventType, payloadMap));
        } catch (Exception e) {
            Log.w(TAG, "메시지 파싱 실패: " + text + " (" + e.getMessage() + ")");
        }
    }

    private Map<String, Object> toMap(JSONObject obj) {
        Map<String, Object> map = new HashMap<>();
        if (obj == null) return map;
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            map.put(k, obj.opt(k));
        }
        return map;
    }
}
