package com.kidsFriend.data.api;

import com.kidsFriend.data.config.AppConfig;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import android.util.Log;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
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
                    .addInterceptor(buildResultLoggingInterceptor())
                    .addInterceptor(buildLoggingInterceptor())
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
                    .addInterceptor(buildResultLoggingInterceptor())
                    .addInterceptor(buildLoggingInterceptor())
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .build();
        }
    }

    /**
     * 모든 API 요청/응답(URL, 메서드, 헤더, 본문, 상태코드)을 Logcat에 출력합니다.
     * Android Studio Logcat에서 "KF_HTTP" 태그로 필터링하면 통신 과정을 볼 수 있습니다.
     */
    private static HttpLoggingInterceptor buildLoggingInterceptor() {
        HttpLoggingInterceptor interceptor =
                new HttpLoggingInterceptor(message -> Log.d("KF_HTTP", message));
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        return interceptor;
    }

    /**
     * API 호출 성공/실패를 한 줄로 요약해 Logcat에 출력합니다.
     * Android Studio Logcat에서 "KF_API" 태그로 필터링하면 어떤 API가 성공했는지 바로 확인됩니다.
     * 예) ✅ API 성공: POST /api/chat/ai (200)
     */
    private static Interceptor buildResultLoggingInterceptor() {
        return chain -> {
            Request request = chain.request();
            String endpoint = request.method() + " " + request.url().encodedPath();
            try {
                Response response = chain.proceed(request);
                if (response.isSuccessful()) {
                    Log.i("KF_API", "✅ API 성공: " + endpoint + " (" + response.code() + ")");
                } else {
                    Log.w("KF_API", "❌ API 실패: " + endpoint + " (" + response.code() + ")");
                }
                return response;
            } catch (java.io.IOException e) {
                Log.w("KF_API", "❌ API 연결 실패: " + endpoint + " - " + e.getMessage());
                throw e;
            }
        };
    }

    private static String getConfiguredBaseUrl() {
        try {
            return AppConfig.getInstance().getBaseUrl();
        } catch (IllegalStateException exception) {
            return ApiConfig.DEFAULT_API_BASE_URL;
        }
    }
}
