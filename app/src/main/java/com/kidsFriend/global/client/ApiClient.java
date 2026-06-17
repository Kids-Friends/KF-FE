package com.kidsFriend.global.client;

public class ApiClient {
    private ApiClient() {
    }

    public static TemiApiService getService() {
        return RetrofitClient.getService();
    }
}
