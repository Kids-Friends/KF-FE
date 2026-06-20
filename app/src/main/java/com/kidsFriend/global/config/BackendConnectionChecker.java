package com.kidsFriend.global.config;

import android.util.Log;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.kidsFriend.global.client.ApiClient;
import com.kidsFriend.global.client.ApiResponse;

public class BackendConnectionChecker {

    private static final String TAG = "BackendConnection";

    private BackendConnectionChecker() {
    }

    public static void check() {
        String baseUrl = AppConfig.getInstance().getBaseUrl();
        Log.i(TAG, "BE 연결 확인 중... (" + baseUrl + ")");

        ApiClient.getService().health().enqueue(new Callback<ApiResponse<Map<String, String>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, String>>> call, Response<ApiResponse<Map<String, String>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.i(TAG, "BE 연결 성공: " + response.body().data);
                } else {
                    Log.w(TAG, "BE 응답 오류: HTTP " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, String>>> call, Throwable t) {
                Log.w(TAG, "BE 연결 실패: " + t.getMessage());
            }
        });
    }
}