package com.kidsFriend.domain.objectgame.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 물건 맞추기 놀이 — 파이썬 비전 서버와의 직접 WebSocket 연결.
 *
 * <p>KF_BE를 거치지 않는다. 파이썬이 WebSocket 서버를 열고 FE가 클라이언트로 접속한다.</p>
 *
 * <p>FE → 파이썬 (라운드 시작 알림):
 * <pre>{"type":"round_start","target":"apple","targetKo":"사과"}</pre>
 * 파이썬 → FE (감지 결과):
 * <pre>{"type":"detection","label":"apple","confidence":0.93}</pre>
 * </p>
 */
public class VisionSocketClient {
    private static final String TAG = "VisionSocket";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Listener {
        void onConnected();
        /** 파이썬이 감지한 물체 1건. (메인 스레드에서 호출됨) */
        void onDetection(@NonNull String label, double confidence);
        void onClosed(@Nullable String reason);
    }

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket: 무기한 수신
            .pingInterval(15, TimeUnit.SECONDS)
            .build();

    @Nullable private WebSocket webSocket;
    @Nullable private Listener listener;

    public void connect(String wsUrl, @NonNull Listener listener) {
        this.listener = listener;
        Log.i(TAG, "Connecting to vision server: " + wsUrl);

        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket ws, @NonNull Response response) {
                Log.i(TAG, "✅ Vision server connected");
                MAIN.post(() -> { if (VisionSocketClient.this.listener != null) VisionSocketClient.this.listener.onConnected(); });
            }

            @Override
            public void onMessage(@NonNull WebSocket ws, @NonNull String text) {
                Log.d(TAG, "Vision message: " + text);
                handleMessage(text);
            }

            @Override
            public void onClosing(@NonNull WebSocket ws, int code, @NonNull String reason) {
                ws.close(1000, null);
            }

            @Override
            public void onClosed(@NonNull WebSocket ws, int code, @NonNull String reason) {
                Log.w(TAG, "Vision server closed: " + reason);
                MAIN.post(() -> { if (VisionSocketClient.this.listener != null) VisionSocketClient.this.listener.onClosed(reason); });
            }

            @Override
            public void onFailure(@NonNull WebSocket ws, @NonNull Throwable t, @Nullable Response response) {
                Log.e(TAG, "❌ Vision server error: " + t.getMessage());
                MAIN.post(() -> { if (VisionSocketClient.this.listener != null) VisionSocketClient.this.listener.onClosed(t.getMessage()); });
            }
        });
    }

    /** 라운드 시작을 파이썬에 알린다(어떤 물체를 찾는 중인지). 연결 전이면 조용히 무시. */
    public void sendRoundStart(String targetLabel, String targetKo) {
        if (webSocket == null) return;
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "round_start");
            msg.put("target", targetLabel);
            msg.put("targetKo", targetKo);
            webSocket.send(msg.toString());
        } catch (Exception e) {
            Log.w(TAG, "sendRoundStart failed", e);
        }
    }

    private void handleMessage(String text) {
        try {
            JSONObject root = new JSONObject(text);
            if (!"detection".equals(root.optString("type", "detection"))) return;
            final String label = root.optString("label", "").trim().toLowerCase();
            final double confidence = root.optDouble("confidence", 1.0);
            if (label.isEmpty()) return;
            MAIN.post(() -> { if (listener != null) listener.onDetection(label, confidence); });
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse vision message: " + text, e);
        }
    }

    public void close() {
        listener = null;
        if (webSocket != null) {
            webSocket.close(1000, "game closed");
            webSocket = null;
        }
    }
}
