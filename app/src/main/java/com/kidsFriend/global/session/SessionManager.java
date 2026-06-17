package com.kidsFriend.global.session;

import android.content.Context;
import android.content.SharedPreferences;

public class SessionManager {
    private static final String PREF_NAME = "kids_friend_session";
    private static final String KEY_ROBOT_ID = "robot_id";
    private static final String KEY_CURRENT_CLIENT_ID = "current_client_id";

    private static final String DEFAULT_ROBOT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String DEFAULT_CLIENT_ID = "00000000-0000-0000-0000-000000000101";

    private static SessionManager instance;

    private final SharedPreferences preferences;

    private SessionManager(Context context) {
        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        try {
            ensureDefaults();
        } catch (Exception e) {
            android.util.Log.e("SessionManager", "Failed to init defaults", e);
        }
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public String getRobotId() {
        return preferences.getString(KEY_ROBOT_ID, DEFAULT_ROBOT_ID);
    }

    public void setRobotId(String robotId) {
        preferences.edit().putString(KEY_ROBOT_ID, robotId).apply();
    }

    public String getCurrentClientId() {
        return preferences.getString(KEY_CURRENT_CLIENT_ID, DEFAULT_CLIENT_ID);
    }

    public void setCurrentClientId(String clientId) {
        preferences.edit().putString(KEY_CURRENT_CLIENT_ID, clientId).apply();
    }

    private void ensureDefaults() {
        SharedPreferences.Editor editor = preferences.edit();
        if (!preferences.contains(KEY_ROBOT_ID)) {
            editor.putString(KEY_ROBOT_ID, DEFAULT_ROBOT_ID);
        }
        if (!preferences.contains(KEY_CURRENT_CLIENT_ID)) {
            editor.putString(KEY_CURRENT_CLIENT_ID, DEFAULT_CLIENT_ID);
        }
        editor.apply();
    }
}
