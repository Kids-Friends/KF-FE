package com.kidsFriend.domain.objectgame.service;

import android.content.Context;
import android.content.SharedPreferences;

import com.kidsFriend.BuildConfig;

/**
 * 물건 맞추기 놀이용 비전 센서(파이썬) 연결 설정.
 *
 * <p>센서는 KF_BE를 거치지 않고 파이썬 코드와 FE가 직접 송수신한다.
 * 파이썬이 WebSocket 서버를 열고, FE가 {@code ws://<파이썬IP>:<포트>} 로 접속한다.
 * 주소는 SharedPreferences에 저장하며 게임 화면의 "연결 설정"에서 바꿀 수 있다.</p>
 */
public final class VisionConfig {
    /** 파이썬 비전 서버 기본 주소(.env의 VISION_WS_URL 주입). 실제 IP는 게임 화면 "연결 설정"에서도 바꿀 수 있다. */
    public static final String DEFAULT_VISION_WS_URL = BuildConfig.VISION_WS_URL;

    private static final String PREF_NAME = "kids_friend_vision_config";
    private static final String KEY_WS_URL = "vision_ws_url";

    private VisionConfig() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getWsUrl(Context context) {
        return prefs(context).getString(KEY_WS_URL, DEFAULT_VISION_WS_URL);
    }

    /** "192.168.0.10:8765", "192.168.0.10", "ws://..." 등 어떤 입력이든 ws:// 형태로 정규화해 저장한다. */
    public static void setWsUrl(Context context, String value) {
        prefs(context).edit().putString(KEY_WS_URL, normalize(value)).apply();
    }

    public static String normalize(String value) {
        String v = value == null ? "" : value.trim();
        if (v.isEmpty()) return DEFAULT_VISION_WS_URL;
        if (v.startsWith("wss://")) {
            return v;
        }
        if (!v.startsWith("ws://")) {
            v = "ws://" + v;
        }
        // 포트가 없으면 기본 포트(8765)를 붙인다.
        String hostPort = v.substring("ws://".length());
        if (!hostPort.contains(":")) {
            v = "ws://" + hostPort + ":8765";
        }
        return v;
    }
}
