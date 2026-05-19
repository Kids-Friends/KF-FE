package com.kidsFriend.data.api;

import com.kidsFriend.data.config.AppConfig;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit;
    private static TemiApiService service;

    private RetrofitClient() {
    }

    public static synchronized TemiApiService getService() {
        if (retrofit == null) {
            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request request = chain.request().newBuilder()
                                .header("ngrok-skip-browser-warning", "true")
                                .build();
                        return chain.proceed(request);
                    })
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(getConfiguredBaseUrl())
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            service = retrofit.create(TemiApiService.class);
        }
        return service;
    }

    public static synchronized void resetInstance() {
        retrofit = null;
        service = null;
    }

    private static String getConfiguredBaseUrl() {
        try {
            return AppConfig.getInstance().getBaseUrl();
        } catch (IllegalStateException exception) {
            return ApiConfig.DEFAULT_API_BASE_URL;
        }
    }
}
