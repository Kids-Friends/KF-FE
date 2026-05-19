package com.kidsFriend.data.api;

import com.kidsFriend.data.config.AppConfig;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

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
            OkHttpClient okHttpClient = buildOkHttpClient();
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

    private static OkHttpClient buildOkHttpClient() {
        try {
            // 개발 환경: ngrok 자체 서명 인증서 신뢰 (프로덕션에서는 제거)
            X509TrustManager trustAllCerts = new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllCerts}, new SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAllCerts)
                    .hostnameVerifier((hostname, session) -> true)
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

        } catch (Exception e) {
            // SSL 설정 실패 시 기본 클라이언트 반환
            return new OkHttpClient.Builder()
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
        }
    }

    private static String getConfiguredBaseUrl() {
        try {
            return AppConfig.getInstance().getBaseUrl();
        } catch (IllegalStateException exception) {
            return ApiConfig.DEFAULT_API_BASE_URL;
        }
    }
}
