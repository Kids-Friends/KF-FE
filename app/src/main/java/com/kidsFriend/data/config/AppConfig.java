package com.kidsFriend.data.config;

import android.content.Context;
import android.content.SharedPreferences;
import com.kidsFriend.BuildConfig;

public class AppConfig {
    // .env 파일의 API_BASE_URL 값이 빌드 시 주입됩니다
    public static final String DEFAULT_BASE_URL = BuildConfig.API_BASE_URL;

    private static final String PREF_NAME = "kids_friend_app_config";
    private static final String KEY_BASE_URL = "base_url";

    private static AppConfig instance;

    private final SharedPreferences preferences;

    private AppConfig(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
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
        if (!normalized.matches("^https://[^/]+/?$") &&
                !normalized.matches("^https?://[^/]+:\\d+/?$")) {
            normalized = normalized.replaceAll("/+$", "") + ":8081/";
        }
        if (!normalized.endsWith("/")) {
            normalized = normalized + "/";
        }
        return normalized;
    }
}
