package com.kidsFriend.data.api;

import com.kidsFriend.data.model.ApiResponse;
import com.kidsFriend.data.model.CallStatusRequest;
import com.kidsFriend.data.model.CallRequest;
import com.kidsFriend.data.model.CallResponse;
import com.kidsFriend.data.model.ChatAiRequest;
import com.kidsFriend.data.model.ChatAiResponse;
import com.kidsFriend.data.model.ChatLogRequest;
import com.kidsFriend.data.model.ClientResponse;
import com.kidsFriend.data.model.PhotoRequest;
import com.kidsFriend.data.model.PointRequest;
import com.kidsFriend.data.model.RobotStatusRequest;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.Path;
import retrofit2.http.POST;

public interface TemiApiService {
    @GET("api/clients")
    Call<ApiResponse<List<ClientResponse>>> getClients();

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

    @POST("api/calls")
    Call<ApiResponse<CallResponse>> createCall(@Body CallRequest request);

    @PATCH("api/calls/{id}/status")
    Call<ApiResponse<CallResponse>> updateCallStatus(
            @Path("id") String callsId,
            @Body CallStatusRequest request
    );

    @POST("api/chat/ai")
    Call<ApiResponse<ChatAiResponse>> askAi(@Body ChatAiRequest request);

    @POST("api/chat")
    Call<ApiResponse<Void>> saveChatLog(@Body ChatLogRequest request);

    @POST("api/photos")
    Call<ApiResponse<Void>> savePhoto(@Body PhotoRequest request);
}
