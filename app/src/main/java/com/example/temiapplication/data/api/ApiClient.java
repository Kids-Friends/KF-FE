package com.example.temiapplication.data.api;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    // TODO: Replace with the Spring Boot server address reachable from the Temi device.
    private static final String BASE_URL = "http://10.0.2.2:8080/";

    private static Retrofit retrofit;

    private ApiClient() {
    }

    public static TemiApiService getService() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(TemiApiService.class);
    }
}
