package com.kidsFriend.data.api;

public class ApiClient {
    private ApiClient() {
    }

    public static TemiApiService getService() {
        return RetrofitClient.getService();
    }
}
