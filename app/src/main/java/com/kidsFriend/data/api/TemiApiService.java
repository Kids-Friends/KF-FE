package com.kidsFriend.data.api;

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
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface TemiApiService {
    @POST("api/calls")
    Call<CallResponse> createCall(@Body CallRequest request);

    @POST("api/questions")
    Call<QuestionResponse> askQuestion(@Body QuestionRequest request);

    @POST("api/questions/voice")
    Call<QuestionResponse> askVoiceQuestion(@Body VoiceQuestionRequest request);

    @GET("api/quizzes/current")
    Call<QuizQuestion> getCurrentQuiz();

    @POST("api/quizzes/answers")
    Call<QuizAnswerResponse> submitQuizAnswer(@Body QuizAnswerRequest request);

    @GET("api/statistics/summary")
    Call<StatisticsSummary> getStatisticsSummary();
}
