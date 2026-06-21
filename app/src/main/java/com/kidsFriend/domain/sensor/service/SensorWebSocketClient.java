package com.kidsFriend.domain.sensor.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.json.JSONObject;

/**
 * 센서 데이터를 실시간으로 수신하기 위한 WebSocket 클라이언트.
 * KF_BE의 /ws/sensors 엔드포인트에 연결하여 하드웨어(KF_AD) 이벤트를 받는다.
 *
 * 수신 메시지 봉투(envelope) 형식:
 *   {"robotId":"TEMI_01","eventType":"<type>","payload":{...},"receivedAt":"..."}
 *
 * eventType 별 처리:
 *   - AIR_QUALITY / DUST_HIGH : payload.grade 로 공기질 등급 캐시 ("공기 확인" 시 사용)
 *   - QUIZ_ANSWER             : payload.answer(O/X) 를 게임 화면 리스너로 전달
 *   - FIRE_ALARM              : payload.status(DETECTED/CLEARED) 를 화재 리스너로 전달
 *   - 그 외(OBSTACLE/CHILD 등): BE 로깅 전용 → FE 무반응
 */
public class SensorWebSocketClient {
    private static final String TAG = "SensorWS";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static String lastAirQualityGrade = null;
    private static WebSocket webSocket;
    private static final OkHttpClient client = buildUnsafeOkHttpClient();

    // ── 비전 O/X 정답 리스너 (게임 화면이 떠 있는 동안만 등록) ──
    public interface QuizAnswerListener {
        void onQuizAnswer(String answer);  // "O" 또는 "X"
    }
    private static volatile QuizAnswerListener quizAnswerListener;

    public static void setQuizAnswerListener(@Nullable QuizAnswerListener listener) {
        quizAnswerListener = listener;
    }

    // ── 화재경보 리스너 (앱 전역 런처 + 경고 화면이 함께 구독) ──
    public interface FireAlarmListener {
        void onFireAlarm(boolean detected);  // true=DETECTED, false=CLEARED
    }
    private static final List<FireAlarmListener> fireListeners = new CopyOnWriteArrayList<>();

    public static void addFireAlarmListener(FireAlarmListener listener) {
        if (listener != null && !fireListeners.contains(listener)) {
            fireListeners.add(listener);
        }
    }

    public static void removeFireAlarmListener(FireAlarmListener listener) {
        fireListeners.remove(listener);
    }

    private static OkHttpClient buildUnsafeOkHttpClient() {
        try {
            X509TrustManager trustAllCerts = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllCerts}, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAllCerts)
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            return new OkHttpClient();
        }
    }

    /**
     * 현재 수신된 최신 미세먼지 등급을 반환합니다. (좋음/보통/나쁨 등)
     * 데이터가 없으면 null을 반환합니다.
     */
    @Nullable
    public static String airQualityGradeOrNull() {
        return lastAirQualityGrade;
    }

    /**
     * WebSocket 연결을 시작합니다.
     * @param baseUrl API 베이스 URL (예: https://.../)
     */
    public static void start(String baseUrl) {
        if (webSocket != null) return;

        String wsUrl = baseUrl.replace("http", "ws") + "ws/sensors";
        Log.i(TAG, "Connecting to WebSocket: " + wsUrl);

        Request request = new Request.Builder()
                .url(wsUrl)
                .header("ngrok-skip-browser-warning", "true")
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                Log.i(TAG, "✅ WebSocket Connected");
            }

            @Override
            public void onMessage(@NonNull WebSocket webSocket, @NonNull String text) {
                Log.d(TAG, "Message received: " + text);
                handleMessage(text);
            }

            @Override
            public void onClosing(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                webSocket.close(1000, null);
                Log.w(TAG, "WebSocket Closing: " + reason);
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "❌ WebSocket Error: " + t.getMessage());
                SensorWebSocketClient.webSocket = null;
            }
        });
    }

    /** 수신 메시지(봉투)를 파싱해 eventType 별로 분기한다. (OkHttp 백그라운드 스레드에서 호출됨) */
    private static void handleMessage(String text) {
        try {
            JSONObject root = new JSONObject(text);
            String eventType = root.optString("eventType", "");
            JSONObject payload = root.optJSONObject("payload");
            if (payload == null) payload = new JSONObject();

            switch (eventType) {
                case "AIR_QUALITY":
                case "DUST_HIGH":
                    updateAirGrade(payload);
                    break;
                case "QUIZ_ANSWER":
                    dispatchQuizAnswer(payload.optString("answer", ""));
                    break;
                case "FIRE_ALARM":
                    dispatchFireAlarm(payload.optString("status", ""));
                    break;
                default:
                    // OBSTACLE_DETECTED, CHILD_DETECTED, VISION_DETECTED 등은
                    // BE 로깅 전용이므로 FE 는 반응하지 않는다.
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse sensor message: " + text, e);
        }
    }

    /** AIR_QUALITY/DUST_HIGH payload.grade → 공기질 등급 캐시 갱신 */
    private static void updateAirGrade(JSONObject payload) {
        String grade = payload.optString("grade", "").trim();
        if (grade.equalsIgnoreCase("GOOD") || grade.equals("좋음")) {
            lastAirQualityGrade = "좋음";
        } else if (grade.equalsIgnoreCase("NORMAL") || grade.equals("보통")) {
            lastAirQualityGrade = "보통";
        } else if (grade.equalsIgnoreCase("BAD") || grade.equals("나쁨")) {
            lastAirQualityGrade = "나쁨";
        }
        // grade 가 없으면 기존 값을 유지한다.
    }

    /** 비전 O/X 정답을 게임 화면 리스너로 (메인 스레드에서) 전달 */
    private static void dispatchQuizAnswer(String answer) {
        if (answer == null) return;
        final String a = answer.trim().toUpperCase();
        if (!a.equals("O") && !a.equals("X")) return;
        MAIN.post(() -> {
            QuizAnswerListener l = quizAnswerListener;
            if (l != null) l.onQuizAnswer(a);
        });
    }

    /** 화재경보 상태를 모든 화재 리스너로 (메인 스레드에서) 전달 */
    private static void dispatchFireAlarm(String status) {
        final boolean detected = "DETECTED".equalsIgnoreCase(status == null ? "" : status.trim());
        MAIN.post(() -> {
            for (FireAlarmListener l : fireListeners) {
                l.onFireAlarm(detected);
            }
        });
    }

    public static void stop() {
        if (webSocket != null) {
            webSocket.close(1000, "App closed");
            webSocket = null;
        }
    }
}
