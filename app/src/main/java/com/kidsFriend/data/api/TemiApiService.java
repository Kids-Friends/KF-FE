package com.kidsFriend.data.api;

import com.kidsFriend.data.model.ApiResponse;
import com.kidsFriend.data.model.ChatAiRequest;
import com.kidsFriend.data.model.ChatAiResponse;
import com.kidsFriend.data.model.ChatLogRequest;
import com.kidsFriend.data.model.ClientResponse;
import com.kidsFriend.data.model.ChatResponse;
import com.kidsFriend.data.model.PhotoRequest;
import com.kidsFriend.data.model.PhotoResponse;
import com.kidsFriend.data.model.PointRequest;
import com.kidsFriend.data.model.RobotStatusRequest;
import com.kidsFriend.data.model.SensorEventRequest;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface TemiApiService {
    @GET("api/clients")
    Call<ApiResponse<List<ClientResponse>>> getClients();

    @GET("api/clients")
    Call<ApiResponse<List<ClientResponse>>> getClientsByName(
            @Query("name") String name
    );

    @GET("api/clients/{id}")
    Call<ApiResponse<ClientResponse>> getClient(@Path("id") String clientId);

    @PATCH("api/clients/{id}/point")
    Call<ApiResponse<ClientResponse>> addClientPoint(
            @Path("id") String clientId,
            @Body PointRequest request
    );

    @PATCH("api/robots/{id}/status")
    Call<ApiResponse<Void>> updateRobotStatus(
            @Path("id") String robotId,
            @Body RobotStatusRequest request
    );

    @POST("api/chat/ai")
    Call<ApiResponse<ChatAiResponse>> askAi(@Body ChatAiRequest request);

    @POST("api/chat")
    Call<ApiResponse<ChatResponse>> saveChatLog(@Body ChatLogRequest request);

    @POST("api/photos")
    Call<ApiResponse<PhotoResponse>> savePhoto(@Body PhotoRequest request);

    @POST("api/sensor-events")
    Call<ApiResponse<Map<String, Object>>> postSensorEvent(@Body SensorEventRequest request);

    @GET("api/sensor-events/latest")
    Call<ApiResponse<Map<String, Object>>> getLatestSensorEvent();

    @GET("api/health")
    Call<Map<String, String>> health();
}
