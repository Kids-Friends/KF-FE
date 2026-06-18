package com.kidsFriend.global.config;

import android.util.Log;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.kidsFriend.global.client.ApiClient;

public class BackendConnectionChecker {

    private static final String TAG = "BackendConnection";

    private BackendConnectionChecker() {
    }

    public static void check() {
        String baseUrl = AppConfig.getInstance().getBaseUrl();
        Log.i(TAG, "BE 연결 확인 중... (" + baseUrl + ")");

        ApiClient.getService().health().enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful()) {
                    Log.i(TAG, "BE 연결 성공: " + response.body());
                } else {
                    Log.w(TAG, "BE 응답 오류: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Log.w(TAG, "BE 연결 실패: " + t.getMessage());
            }
        });
    }
}