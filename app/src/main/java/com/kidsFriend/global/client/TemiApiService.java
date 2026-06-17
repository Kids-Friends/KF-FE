package com.kidsFriend.global.client;

import com.kidsFriend.global.client.ApiResponse;
import com.kidsFriend.domain.chat.ChatAiRequest;
import com.kidsFriend.domain.chat.ChatAiResponse;
import com.kidsFriend.domain.chat.ChatLogRequest;
import com.kidsFriend.domain.membership.ClientResponse;
import com.kidsFriend.domain.chat.ChatResponse;
import com.kidsFriend.domain.greeting.IntentRequest;
import com.kidsFriend.domain.greeting.IntentResponse;
import com.kidsFriend.domain.membership.PhotoRequest;
import com.kidsFriend.domain.membership.PhotoResponse;
import com.kidsFriend.domain.membership.PointRequest;
import com.kidsFriend.domain.sensor.RobotLocationsRequest;
import com.kidsFriend.domain.sensor.RobotPositionRequest;
import com.kidsFriend.domain.sensor.SensorEventRequest;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface TemiApiService {
    @GET("api/clients")
    Call<ApiResponse<List<ClientResponse>>> getClients();

    @GET("api/clients/search")
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

    @POST("api/chat/ai")
    Call<ApiResponse<ChatAiResponse>> askAi(@Body ChatAiRequest request);

    @POST("api/intent")
    Call<ApiResponse<IntentResponse>> resolveIntent(@Body IntentRequest request);

    @POST("api/chat")
    Call<ApiResponse<ChatResponse>> saveChatLog(@Body ChatLogRequest request);

    @Multipart
    @POST("api/photos/upload")
    Call<ApiResponse<PhotoResponse>> uploadPhoto(
            @Part MultipartBody.Part file
    );

    @POST("api/photos")
    Call<ApiResponse<PhotoResponse>> savePhoto(@Body PhotoRequest request);

    @POST("api/sensor-events")
    Call<ApiResponse<Map<String, Object>>> postSensorEvent(@Body SensorEventRequest request);

    @GET("api/sensor-events/latest")
    Call<ApiResponse<Map<String, Object>>> getLatestSensorEvent();

    @POST("api/robot-position")
    Call<Void> reportRobotPosition(@Body RobotPositionRequest request);

    @POST("api/robot-position/locations")
    Call<Void> reportRobotLocations(@Body RobotLocationsRequest request);

    @GET("api/health")
    Call<Map<String, String>> health();
}
