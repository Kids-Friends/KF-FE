package com.kidsFriend.data.repository;

import com.kidsFriend.data.api.ApiClient;
import com.kidsFriend.data.api.ApiConfig;
import com.kidsFriend.data.api.TemiApiService;
import com.kidsFriend.data.mock.MockDataSource;
import com.kidsFriend.data.model.CallRequest;
import com.kidsFriend.data.model.CallResponse;
import com.kidsFriend.data.model.QuestionRequest;
import com.kidsFriend.data.model.QuestionResponse;
import com.kidsFriend.data.model.QuizAnswerRequest;
import com.kidsFriend.data.model.QuizAnswerResponse;
import com.kidsFriend.data.model.QuizQuestion;
import com.kidsFriend.data.model.StatisticsSummary;
import com.kidsFriend.data.model.VoiceQuestionRequest;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TemiRepository {
    private final TemiApiService apiService;
    private final MockDataSource mockDataSource;

    public TemiRepository() {
        this.apiService = ApiClient.getService();
        this.mockDataSource = new MockDataSource();
    }

    public void createCall(String reason, RepositoryCallback<CallResponse> callback) {
        CallRequest request = new CallRequest(reason);
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.createCall(request));
            return;
        }
        apiService.createCall(request).enqueue(toRetrofitCallback(callback));
    }

    public void askQuestion(String question, RepositoryCallback<QuestionResponse> callback) {
        QuestionRequest request = new QuestionRequest(question);
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.askQuestion(request));
            return;
        }
        apiService.askQuestion(request).enqueue(toRetrofitCallback(callback));
    }

    public void askVoiceQuestion(String rawText, String reconstructedText, RepositoryCallback<QuestionResponse> callback) {
        VoiceQuestionRequest request = new VoiceQuestionRequest(rawText, reconstructedText);
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.askVoiceQuestion(request));
            return;
        }
        apiService.askVoiceQuestion(request).enqueue(toRetrofitCallback(callback));
    }

    public void getCurrentQuiz(RepositoryCallback<QuizQuestion> callback) {
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.getCurrentQuiz());
            return;
        }
        apiService.getCurrentQuiz().enqueue(toRetrofitCallback(callback));
    }

    public void submitQuizAnswer(String quizId, String selectedAnswer, RepositoryCallback<QuizAnswerResponse> callback) {
        QuizAnswerRequest request = new QuizAnswerRequest(quizId, selectedAnswer);
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.submitQuizAnswer(request));
            return;
        }
        apiService.submitQuizAnswer(request).enqueue(toRetrofitCallback(callback));
    }

    public void getStatisticsSummary(RepositoryCallback<StatisticsSummary> callback) {
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.getStatisticsSummary());
            return;
        }
        apiService.getStatisticsSummary().enqueue(toRetrofitCallback(callback));
    }

    private <T> Callback<T> toRetrofitCallback(RepositoryCallback<T> callback) {
        return new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                    return;
                }
                callback.onError("API 응답 오류: " + response.code());
            }

            @Override
            public void onFailure(Call<T> call, Throwable t) {
                callback.onError("API 연결 실패: " + t.getMessage());
            }
        };
    }
}
