package com.kidsFriend.global.client;

import java.util.Map;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import com.kidsFriend.domain.chat.response.ChatAiRequest;
import com.kidsFriend.domain.chat.response.ChatAiResponse;
import com.kidsFriend.domain.greeting.request.IntentRequest;
import com.kidsFriend.domain.greeting.response.IntentResponse;

public interface TemiApiService {
    @POST("api/chat/ai")
    Call<ApiResponse<ChatAiResponse>> askAi(@Body ChatAiRequest request);

    @POST("api/intent")
    Call<ApiResponse<IntentResponse>> resolveIntent(@Body IntentRequest request);

    @GET("api/health")
    Call<Map<String, String>> health();
}
