package com.kidsFriend.global.client;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import retrofit2.http.Headers;

import com.kidsFriend.domain.chat.response.ChatAiRequest;
import com.kidsFriend.domain.chat.response.ChatAiResponse;
public interface TemiApiService {
    @POST("api/chat/ai")
    @Headers("ngrok-skip-browser-warning: true")
    Call<ApiResponse<ChatAiResponse>> askAi(@Body ChatAiRequest request);

    @GET("api/health")
    @Headers("ngrok-skip-browser-warning: true")
    Call<ApiResponse<Map<String, String>>> health();
}
