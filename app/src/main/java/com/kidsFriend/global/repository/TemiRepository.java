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
        if (ApiConfig.USE_MOCK) {
            QuestionRequest request = new QuestionRequest(question);
            callback.onSuccess(mockDataSource.askQuestion(request));
            return;
        }

        ChatAiRequest request = new ChatAiRequest(question);
        apiService().askAi(request).enqueue(toQuestionCallback(callback));
    }

    public void askVoiceQuestion(String rawText, String reconstructedText, RepositoryCallback<QuestionResponse> callback) {
        if (ApiConfig.USE_MOCK) {
            VoiceQuestionRequest request = new VoiceQuestionRequest(rawText, reconstructedText);
            callback.onSuccess(mockDataSource.askVoiceQuestion(request));
            return;
        }

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
     * 미세먼지(공기질) 등급을 반환한다. 백엔드 sensor-events API가 제거되어 시연용 고정값("보통")으로 안내한다.
     */
    public void getAirQuality(RepositoryCallback<String> callback) {
        callback.onSuccess("보통");
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
                    callback.onSuccess(new QuestionResponse(true, "서버랑 잠깐 연결이 끊어졌나 봐. 다시 한번 말해줄래?", "now"));
                    return;
                }

                ApiResponse<ChatAiResponse> body = response.body();
                if (body == null || body.data == null || body.data.reply == null) {
                    Log.e("TemiRepository", "API response body is empty.");
                    callback.onSuccess(new QuestionResponse(true, "어라, 내가 무슨 말을 하려고 했는지 까먹었어. 헤헤.", "now"));
                    return;
                }

                callback.onSuccess(new QuestionResponse(true, body.data.reply, body.data.createdAt));
            }

            @Override
            public void onFailure(Call<ApiResponse<ChatAiResponse>> call, Throwable t) {
                Log.e("TemiRepository", "API connection failed: " + t.getMessage(), t);
                callback.onSuccess(new QuestionResponse(true, "앗, 지금 인터넷 연결이 안 좋아서 조금 헷갈려. 하지만 난 항상 네 친구야!", "now"));
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
