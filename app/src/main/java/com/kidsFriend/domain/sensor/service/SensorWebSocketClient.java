package com.kidsFriend.domain.sensor.service;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 센서 데이터를 실시간으로 수신하기 위한 WebSocket 클라이언트.
 * KF_BE의 /ws/sensors 엔드포인트에 연결하여 공기질 등의 정보를 업데이트합니다.
 */
public class SensorWebSocketClient {
    private static final String TAG = "SensorWS";
    private static String lastAirQualityGrade = null;
    private static WebSocket webSocket;
    private static final OkHttpClient client = buildUnsafeOkHttpClient();

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
                parseSensorData(text);
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

    private static void parseSensorData(String json) {
        try {
            // 시나리오 2.6: 센서 데이터 파싱 및 등급 업데이트
            // 실제 구현에서는 JSON 파싱 라이브러리를 사용해야 합니다.
            if (json.contains("\"grade\":\"GOOD\"") || json.contains("\"grade\":\"좋음\"")) {
                lastAirQualityGrade = "좋음";
            } else if (json.contains("\"grade\":\"NORMAL\"") || json.contains("\"grade\":\"보통\"")) {
                lastAirQualityGrade = "보통";
            } else if (json.contains("\"grade\":\"BAD\"") || json.contains("\"grade\":\"나쁨\"")) {
                lastAirQualityGrade = "나쁨";
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse sensor data", e);
        }
    }

    public static void stop() {
        if (webSocket != null) {
            webSocket.close(1000, "App closed");
            webSocket = null;
        }
    }
}
