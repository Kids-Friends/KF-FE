package com.kidsFriend.global.config;

import android.content.Context;
import android.content.SharedPreferences;

public class AppConfig {
    // .env 파일의 API_BASE_URL 값이 빌드 시 주입되지만, 수동으로 ngrok 주소를 우선 적용합니다.
    public static final String DEFAULT_BASE_URL = "https://avengeful-shaunte-revolvingly.ngrok-free.dev/";

    private static final String PREF_NAME = "kids_friend_app_config";
    private static final String KEY_BASE_URL = "base_url";

    private static AppConfig instance;

    private final SharedPreferences preferences;

    private AppConfig(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        // 앱 시작 시 항상 최신 ngrok 주소로 초기화하도록 강제함 (빌드 캐시 문제 해결)
        preferences.edit().putString(KEY_BASE_URL, DEFAULT_BASE_URL).apply();
    }

    public static synchronized AppConfig init(Context context) {
        if (instance == null) {
            instance = new AppConfig(context);
        }
        return instance;
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AppConfig must be initialized with context first.");
        }
        return instance;
    }

    public String getBaseUrl() {
        return preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public void updateBaseUrl(String baseUrl) {
        preferences.edit().putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl)).apply();
    }

    public String buildBaseUrlFromIp(String ipAddress) {
        return normalizeBaseUrl(ipAddress);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            normalized = DEFAULT_BASE_URL;
        }
        
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "http://" + normalized;
        }

        // ngrok URL인 경우 포트 8080을 붙이지 않도록 예외 처리
        if (normalized.contains("ngrok-free.dev") || normalized.contains("ngrok.io")) {
            if (!normalized.endsWith("/")) {
                normalized += "/";
            }
            return normalized;
        }

        // 일반 IP 주소인 경우에만 포트 8080을 자동으로 붙임
        if (!normalized.matches("^https?://[^/]+:\\d+/?$")) {
            normalized = normalized.replaceAll("/+$", "") + ":8080/";
        }
        
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}