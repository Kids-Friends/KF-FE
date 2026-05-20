package com.kidsFriend.data.repository;

import android.content.Context;

import com.kidsFriend.data.api.ApiClient;
import com.kidsFriend.data.api.ApiConfig;
import com.kidsFriend.data.api.TemiApiService;
import com.kidsFriend.data.config.AppConfig;
import com.kidsFriend.data.mock.MockDataSource;
import com.kidsFriend.data.model.ApiResponse;
import com.kidsFriend.data.model.CallStatusRequest;
import com.kidsFriend.data.model.CallRequest;
import com.kidsFriend.data.model.CallResponse;
import com.kidsFriend.data.model.ChatAiRequest;
import com.kidsFriend.data.model.ChatAiResponse;
import com.kidsFriend.data.model.ChatLogRequest;
import com.kidsFriend.data.model.ChatResponse;
import com.kidsFriend.data.model.ClientResponse;
import com.kidsFriend.data.model.PhotoRequest;
import com.kidsFriend.data.model.PhotoResponse;
import com.kidsFriend.data.model.PointRequest;
import com.kidsFriend.data.model.QuestionRequest;
import com.kidsFriend.data.model.QuestionResponse;
import com.kidsFriend.data.model.QuizAnswerRequest;
import com.kidsFriend.data.model.QuizAnswerResponse;
import com.kidsFriend.data.model.QuizQuestion;
import com.kidsFriend.data.model.RobotStatusRequest;
import com.kidsFriend.data.model.StatisticsSummary;
import com.kidsFriend.data.model.VoiceQuestionRequest;
import com.kidsFriend.data.model.ZoneInfo;
import com.kidsFriend.data.session.SessionManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TemiRepository {
    private final MockDataSource mockDataSource;
    private final SessionManager sessionManager;

    public TemiRepository(Context context) {
        AppConfig.init(context);
        this.mockDataSource = new MockDataSource();
        this.sessionManager = SessionManager.getInstance(context);
    }

    public TemiRepository() {
        this.mockDataSource = new MockDataSource();
        this.sessionManager = null;
    }

    public void createCall(String reason, RepositoryCallback<CallResponse> callback) {
        CallRequest request = new CallRequest(getRobotId(), getCurrentClientId(), reason);
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.createCall(request));
            return;
        }
        apiService().createCall(request).enqueue(toWrappedRetrofitCallback(callback));
    }

    public void updateCallStatus(String callsId, String status, RepositoryCallback<CallResponse> callback) {
        apiService().updateCallStatus(callsId, new CallStatusRequest(status))
                .enqueue(toWrappedRetrofitCallback(callback));
    }

    public void askQuestion(String question, RepositoryCallback<QuestionResponse> callback) {
        if (ApiConfig.USE_MOCK) {
            QuestionRequest request = new QuestionRequest(question);
            callback.onSuccess(mockDataSource.askQuestion(request));
            return;
        }

        apiService().askAi(new ChatAiRequest(question)).enqueue(toQuestionCallback(question, callback));
    }

    public void askVoiceQuestion(String rawText, String reconstructedText, RepositoryCallback<QuestionResponse> callback) {
        VoiceQuestionRequest request = new VoiceQuestionRequest(rawText, reconstructedText);
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.askVoiceQuestion(request));
            return;
        }
        apiService().askAi(new ChatAiRequest(rawText)).enqueue(toQuestionCallback(rawText, callback));
    }

    public void getCurrentQuiz(RepositoryCallback<QuizQuestion> callback) {
        callback.onSuccess(mockDataSource.getCurrentQuiz());
    }

    public void submitQuizAnswer(String quizId, String selectedAnswer, RepositoryCallback<QuizAnswerResponse> callback) {
        QuizAnswerRequest request = new QuizAnswerRequest(quizId, selectedAnswer);
        QuizAnswerResponse response = mockDataSource.submitQuizAnswer(request);
        if (!response.correct || ApiConfig.USE_MOCK) {
            callback.onSuccess(response);
            return;
        }

        addPointToCurrentClient(new RepositoryCallback<ClientResponse>() {
            @Override
            public void onSuccess(ClientResponse data) {
                callback.onSuccess(response);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    public void getStatisticsSummary(RepositoryCallback<StatisticsSummary> callback) {
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(mockDataSource.getStatisticsSummary());
            return;
        }
        callback.onSuccess(mockDataSource.getStatisticsSummary());
    }

    public void getZones(RepositoryCallback<List<ZoneInfo>> callback) {
        callback.onSuccess(mockDataSource.getZones());
    }

    public void getClients(RepositoryCallback<List<ClientResponse>> callback) {
        apiService().getClients().enqueue(toWrappedRetrofitCallback(callback));
    }

    public void getClient(String clientId, RepositoryCallback<ClientResponse> callback) {
        apiService().getClient(clientId).enqueue(toWrappedRetrofitCallback(callback));
    }

    public void addPointToCurrentClient(RepositoryCallback<ClientResponse> callback) {
        addClientPoint(getCurrentClientId(), 1, callback);
    }

    public void addClientPoint(String clientId, int amount, RepositoryCallback<ClientResponse> callback) {
        apiService().addClientPoint(clientId, new PointRequest(amount))
                .enqueue(toWrappedRetrofitCallback(callback));
    }

    public void updateRobotStatus(String status, RepositoryCallback<Void> callback) {
        if (ApiConfig.USE_MOCK) {
            callback.onSuccess(null);
            return;
        }
        apiService().updateRobotStatus(getRobotId(), new RobotStatusRequest(status))
                .enqueue(toVoidWrappedRetrofitCallback(callback));
    }

    public void saveChatLog(String question, String answer, RepositoryCallback<ChatResponse> callback) {
        ChatLogRequest request = new ChatLogRequest(
                getCurrentClientId(),
                getRobotId(),
                question,
                answer,
                "CHAT"
        );
        apiService().saveChatLog(request).enqueue(toWrappedRetrofitCallback(callback));
    }

    public void savePhoto(String photoUrl, String photoName, RepositoryCallback<PhotoResponse> callback) {
        PhotoRequest request = new PhotoRequest(getCurrentClientId(), photoUrl, photoName);
        apiService().savePhoto(request).enqueue(toWrappedRetrofitCallback(callback));
    }

    private TemiApiService apiService() {
        return ApiClient.getService();
    }

    private Callback<ApiResponse<ChatAiResponse>> toQuestionCallback(
            String originalQuestion,
            RepositoryCallback<QuestionResponse> callback
    ) {
        return new Callback<ApiResponse<ChatAiResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ChatAiResponse>> call, Response<ApiResponse<ChatAiResponse>> response) {
                if (!response.isSuccessful()) {
                    callback.onError("API response error: " + response.code());
                    return;
                }

                ApiResponse<ChatAiResponse> body = response.body();
                if (body == null || body.data == null || body.data.reply == null) {
                    callback.onError("API response body is empty.");
                    return;
                }

                QuestionResponse questionResponse = new QuestionResponse(true, body.data.reply, body.data.createdAt);
                saveChatLog(originalQuestion, body.data.reply, new RepositoryCallback<ChatResponse>() {
                    @Override
                    public void onSuccess(ChatResponse data) {
                        callback.onSuccess(questionResponse);
                    }

                    @Override
                    public void onError(String message) {
                        callback.onSuccess(questionResponse);
                    }
                });
            }

            @Override
            public void onFailure(Call<ApiResponse<ChatAiResponse>> call, Throwable t) {
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

    private Callback<ApiResponse<Void>> toVoidWrappedRetrofitCallback(RepositoryCallback<Void> callback) {
        return new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(null);
                    return;
                }
                callback.onError("API response error: " + response.code());
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                callback.onError("API connection failed: " + t.getMessage());
            }
        };
    }

    private String getRobotId() {
        if (sessionManager == null) {
            return "00000000-0000-0000-0000-000000000001";
        }
        return sessionManager.getRobotId();
    }

    private String getCurrentClientId() {
        if (sessionManager == null) {
            return "00000000-0000-0000-0000-000000000101";
        }
        return sessionManager.getCurrentClientId();
    }

}
