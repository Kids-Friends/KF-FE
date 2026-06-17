package com.kidsFriend.data.repository;

import android.content.Context;
import android.util.Log;

import com.kidsFriend.data.api.ApiClient;
import com.kidsFriend.data.api.ApiConfig;
import com.kidsFriend.data.api.TemiApiService;
import com.kidsFriend.data.config.AppConfig;
import com.kidsFriend.data.mock.MockDataSource;
import com.kidsFriend.data.model.ApiResponse;
import com.kidsFriend.data.model.ChatAiRequest;
import com.kidsFriend.data.model.ChatAiResponse;
import com.kidsFriend.data.model.ChatLogRequest;
import com.kidsFriend.data.model.ChatResponse;
import com.kidsFriend.data.model.ClientResponse;
import com.kidsFriend.data.model.IntentRequest;
import com.kidsFriend.data.model.IntentResponse;
import com.kidsFriend.data.model.PhotoRequest;
import com.kidsFriend.data.model.PhotoResponse;
import com.kidsFriend.data.model.PointRequest;
import com.kidsFriend.data.model.QuestionRequest;
import com.kidsFriend.data.model.QuestionResponse;
import com.kidsFriend.data.model.QuizAnswerRequest;
import com.kidsFriend.data.model.QuizAnswerResponse;
import com.kidsFriend.data.model.QuizQuestion;
import com.kidsFriend.data.model.VoiceQuestionRequest;
import com.kidsFriend.data.model.ZoneInfo;
import com.kidsFriend.data.session.SessionManager;

import java.io.File;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
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

    public void askQuestion(String question, RepositoryCallback<QuestionResponse> callback) {
        if (ApiConfig.USE_MOCK) {
            QuestionRequest request = new QuestionRequest(question);
            callback.onSuccess(mockDataSource.askQuestion(request));
            return;
        }

        String clientId = getCurrentClientId();
        // 기본 Guest ID인 경우 서버에 clientId를 보내지 않거나 선택적으로 처리
        ChatAiRequest request = new ChatAiRequest(question);
        apiService().askAi(request).enqueue(toQuestionCallback(question, callback));
    }

    public void askVoiceQuestion(String rawText, String reconstructedText, RepositoryCallback<QuestionResponse> callback) {
        if (ApiConfig.USE_MOCK) {
            VoiceQuestionRequest request = new VoiceQuestionRequest(rawText, reconstructedText);
            callback.onSuccess(mockDataSource.askVoiceQuestion(request));
            return;
        }
        
        String clientId = getCurrentClientId();
        ChatAiRequest request = new ChatAiRequest(rawText);
        apiService().askAi(request).enqueue(toQuestionCallback(rawText, callback));
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
                // 포인트 적립 실패(회원 없음 등)하더라도 사용자에게는 정답 메시지를 보여줌
                Log.w("TemiRepository", "Point addition failed: " + message);
                callback.onSuccess(response);
            }
        });
    }

    public void getZones(RepositoryCallback<List<ZoneInfo>> callback) {
        callback.onSuccess(mockDataSource.getZones());
    }

    /**
     * 미세먼지(공기질) 등급을 가져온다. 최신 센서 이벤트가 공기질 이벤트면 그 값을 등급으로
     * 변환하고, 값이 없거나 공기질 이벤트가 아니거나 호출이 실패하면 "보통"으로 폴백한다.
     */
    public void getAirQuality(RepositoryCallback<String> callback) {
        apiService().getLatestSensorEvent().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(parseAirGrade(response.body().data));
                } else {
                    callback.onSuccess("보통");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Log.w("TemiRepository", "getAirQuality failed, fallback 보통: " + t.getMessage());
                callback.onSuccess("보통");
            }
        });
    }

    /** 센서 데이터에서 공기질 등급(좋음/보통/나쁨) 추출. 판별 불가 시 "보통". */
    private String parseAirGrade(Map<String, Object> data) {
        if (data == null) {
            return "보통";
        }
        String type = (str(data.get("sensorType")) + str(data.get("eventType"))).toUpperCase();
        boolean isAir = type.contains("DUST") || type.contains("AIR")
                || type.contains("PM") || type.contains("미세");
        if (!isAir) {
            return "보통";
        }
        // value(문자열) 우선, 없으면 payload의 grade/pm25 확인
        String raw = str(data.get("value"));
        if (raw.isEmpty() && data.get("payload") instanceof Map) {
            Map<?, ?> p = (Map<?, ?>) data.get("payload");
            Object grade = p.get("grade");
            Object pm = p.get("pm25");
            if (grade != null) {
                raw = grade.toString();
            } else if (pm != null) {
                raw = pm.toString();
            }
        }
        raw = raw.trim();
        if (raw.contains("좋")) {
            return "좋음";
        }
        if (raw.contains("나쁨") || raw.toUpperCase().contains("BAD")) {
            return "나쁨";
        }
        if (raw.contains("보통") || raw.toUpperCase().contains("NORMAL") || raw.toUpperCase().contains("MODERATE")) {
            return "보통";
        }
        try {
            double pm = Double.parseDouble(raw);
            if (pm <= 15) {
                return "좋음";
            }
            if (pm <= 35) {
                return "보통";
            }
            return "나쁨";
        } catch (NumberFormatException ignored) {
            // 숫자가 아니면 아래 기본값으로
        }
        return "보통";
    }

    private String str(Object o) {
        return o == null ? "" : o.toString();
    }

    public void getClients(RepositoryCallback<List<ClientResponse>> callback) {
        apiService().getClients().enqueue(toWrappedRetrofitCallback(callback));
    }

    public void getClientsByName(String name, RepositoryCallback<List<ClientResponse>> callback) {
        apiService().getClientsByName(name).enqueue(toWrappedRetrofitCallback(callback));
    }

    public void getClient(String clientId, RepositoryCallback<ClientResponse> callback) {
        apiService().getClient(clientId).enqueue(new Callback<ApiResponse<ClientResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ClientResponse>> call, Response<ApiResponse<ClientResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    callback.onSuccess(response.body().data);
                } else {
                    Log.w("TemiRepository", "getClient failed (" + response.code() + "), returning mock fallback");
                    callback.onSuccess(createMockClient(clientId));
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ClientResponse>> call, Throwable t) {
                Log.e("TemiRepository", "getClient network error, returning mock fallback: " + t.getMessage());
                callback.onSuccess(createMockClient(clientId));
            }
        });
    }

    private ClientResponse createMockClient(String clientId) {
        ClientResponse mock = new ClientResponse();
        mock.clientId = clientId;
        mock.childName = "테미친구";
        mock.parentName = "테미보호자";
        mock.parentPhone = "010-0000-0000";
        mock.clientPoint = 1004; // 시연용 포인트
        return mock;
    }

    public void addPointToCurrentClient(RepositoryCallback<ClientResponse> callback) {
        addClientPoint(getCurrentClientId(), 1, callback);
    }

    public void addClientPoint(String clientId, int amount, RepositoryCallback<ClientResponse> callback) {
        apiService().addClientPoint(clientId, new PointRequest(amount))
                .enqueue(new Callback<ApiResponse<ClientResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ClientResponse>> call, Response<ApiResponse<ClientResponse>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                            callback.onSuccess(response.body().data);
                        } else {
                            Log.w("TemiRepository", "addClientPoint failed, using mock");
                            ClientResponse mock = createMockClient(clientId);
                            mock.clientPoint += amount; // 모의로 포인트 증가
                            callback.onSuccess(mock);
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ClientResponse>> call, Throwable t) {
                        Log.e("TemiRepository", "addClientPoint error, using mock: " + t.getMessage());
                        ClientResponse mock = createMockClient(clientId);
                        mock.clientPoint += amount;
                        callback.onSuccess(mock);
                    }
                });
    }

    public void saveChatLog(String question, String answer, RepositoryCallback<ChatResponse> callback) {
        ChatLogRequest request = new ChatLogRequest(
                getCurrentClientId(),
                getRobotId(),
                question,
                answer,
                "CHAT"
        );
        apiService().saveChatLog(request).enqueue(new Callback<ApiResponse<ChatResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<ChatResponse>> call, Response<ApiResponse<ChatResponse>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().data != null) {
                    callback.onSuccess(response.body().data);
                } else {
                    Log.w("TemiRepository", "saveChatLog failed, using mock success");
                    callback.onSuccess(new ChatResponse());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<ChatResponse>> call, Throwable t) {
                Log.e("TemiRepository", "saveChatLog error, using mock success: " + t.getMessage());
                callback.onSuccess(new ChatResponse());
            }
        });
    }

    public void savePhoto(String photoUrl, String photoName, RepositoryCallback<PhotoResponse> callback) {
        PhotoRequest request = new PhotoRequest(getCurrentClientId(), photoUrl, photoName);
        apiService().savePhoto(request).enqueue(toWrappedRetrofitCallback(callback));
    }

    public void uploadPhoto(File file, RepositoryCallback<PhotoResponse> callback) {
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("file", file.getName(), requestFile);
        apiService().uploadPhoto(body).enqueue(toWrappedRetrofitCallback(callback));
    }

    /**
     * Step 1(업로드) + Step 2(DB 등록)를 한 번에 수행하는 통합 메서드
     */
    public void uploadAndSavePhoto(File file, RepositoryCallback<PhotoResponse> callback) {
        uploadPhoto(file, new RepositoryCallback<PhotoResponse>() {
            @Override
            public void onSuccess(PhotoResponse uploadedData) {
                // Step 2: 업로드된 정보를 바탕으로 메타데이터 등록
                savePhoto(uploadedData.photoUrl, uploadedData.photoName, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError("Step 1 Upload failed: " + message);
            }
        });
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
            String originalQuestion,
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

                QuestionResponse questionResponse = new QuestionResponse(true, body.data.reply, body.data.createdAt);
                // 답변은 즉시 전달하고(발화/표시 지연 제거), 채팅 로그는 백그라운드로 저장
                callback.onSuccess(questionResponse);
                saveChatLog(originalQuestion, body.data.reply, new RepositoryCallback<ChatResponse>() {
                    @Override
                    public void onSuccess(ChatResponse data) {
                    }

                    @Override
                    public void onError(String message) {
                        Log.w("TemiRepository", "Chat log save failed: " + message);
                    }
                });
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
