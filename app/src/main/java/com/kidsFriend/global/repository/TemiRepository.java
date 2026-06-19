package com.kidsFriend.global.repository;

import android.content.Context;
import android.util.Log;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.kidsFriend.domain.chat.request.QuestionRequest;
import com.kidsFriend.domain.chat.request.VoiceQuestionRequest;
import com.kidsFriend.domain.chat.response.ChatAiRequest;
import com.kidsFriend.domain.chat.response.ChatAiResponse;
import com.kidsFriend.domain.chat.response.QuestionResponse;
import com.kidsFriend.domain.greeting.request.IntentRequest;
import com.kidsFriend.domain.greeting.response.IntentResponse;
import com.kidsFriend.domain.quiz.request.QuizAnswerRequest;
import com.kidsFriend.domain.quiz.response.QuizAnswerResponse;
import com.kidsFriend.domain.quiz.service.QuizQuestion;
import com.kidsFriend.domain.sensor.service.SensorWebSocketClient;
import com.kidsFriend.global.client.ApiClient;
import com.kidsFriend.global.client.ApiResponse;
import com.kidsFriend.global.client.TemiApiService;
import com.kidsFriend.global.config.ApiConfig;
import com.kidsFriend.global.config.AppConfig;
import com.kidsFriend.global.repository.mock.MockDataSource;

public class TemiRepository {
    private final MockDataSource mockDataSource;

    public TemiRepository(Context context) {
        AppConfig.init(context);
        this.mockDataSource = new MockDataSource();
    }

    public TemiRepository() {
        this.mockDataSource = new MockDataSource();
    }

    public void askQuestion(String question, RepositoryCallback<QuestionResponse> callback) {
        ChatAiRequest request = new ChatAiRequest(question);
        apiService().askAi(request).enqueue(toQuestionCallback(callback));
    }

    public void askVoiceQuestion(String rawText, String reconstructedText, RepositoryCallback<QuestionResponse> callback) {
        ChatAiRequest request = new ChatAiRequest(rawText);
        apiService().askAi(request).enqueue(toQuestionCallback(callback));
    }

    public void getCurrentQuiz(RepositoryCallback<QuizQuestion> callback) {
        callback.onSuccess(mockDataSource.getCurrentQuiz());
    }

    public void submitQuizAnswer(String quizId, String selectedAnswer, RepositoryCallback<QuizAnswerResponse> callback) {
        QuizAnswerRequest request = new QuizAnswerRequest(quizId, selectedAnswer);
        callback.onSuccess(mockDataSource.submitQuizAnswer(request));
    }

    /**
     * 미세먼지(공기질) 등급을 반환한다. WS로 받은 최신 pm25 기반 등급(좋음/보통/나쁨),
     * 값이 없거나 오래됐으면(미연결/타임아웃) "보통"으로 폴백한다(시나리오 2.6: 실패 시 가라).
     */
    public void getAirQuality(RepositoryCallback<String> callback) {
        String grade = SensorWebSocketClient.airQualityGradeOrNull();
        callback.onSuccess(grade != null ? grade : "보통");
    }

    public void askAi(String question, RepositoryCallback<ChatAiResponse> callback) {
        apiService().askAi(new ChatAiRequest(question)).enqueue(toWrappedRetrofitCallback(callback));
    }

    public void resolveIntent(String text, RepositoryCallback<IntentResponse> callback) {
        apiService().resolveIntent(new IntentRequest(text)).enqueue(toWrappedRetrofitCallback(callback));
    }

    private TemiApiService apiService() {
        return ApiClient.getService();
    }

    private Callback<ApiResponse<ChatAiResponse>> toQuestionCallback(
            RepositoryCallback<QuestionResponse> callback
    ) {
        return new Callback<ApiResponse<ChatAiResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ChatAiResponse>> call, Response<ApiResponse<ChatAiResponse>> response) {
                if (!response.isSuccessful()) {
                    Log.e("TemiRepository", "API response error: " + response.code());
                    callback.onError("API response error: " + response.code());
                    return;
                }

                ApiResponse<ChatAiResponse> body = response.body();
                if (body == null || body.data == null || body.data.reply == null) {
                    Log.e("TemiRepository", "API response body is empty.");
                    callback.onError("API response body is empty.");
                    return;
                }

                callback.onSuccess(new QuestionResponse(true, body.data.reply, body.data.createdAt));
            }

            @Override
            public void onFailure(Call<ApiResponse<ChatAiResponse>> call, Throwable t) {
                Log.e("TemiRepository", "API connection failed: " + t.getMessage(), t);
                callback.onError("API connection failed: " + t.getMessage());
            }
        };
    }

    private <T> Callback<ApiResponse<T>> toWrappedRetrofitCallback(RepositoryCallback<T> callback) {
        return new Callback<ApiResponse<T>>() {
            @Override
            public void onResponse(Call<ApiResponse<T>> call, Response<ApiResponse<T>> response) {
                if (!response.isSuccessful()) {
                    callback.onError("API response error: " + response.code());
                    return;
                }

                ApiResponse<T> body = response.body();
                if (body != null && body.data != null) {
                    callback.onSuccess(body.data);
                    return;
                }
                callback.onError("API response body is empty.");
            }

            @Override
            public void onFailure(Call<ApiResponse<T>> call, Throwable t) {
                callback.onError("API connection failed: " + t.getMessage());
            }
        };
    }
}
