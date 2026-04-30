package com.example.temiapplication.data.api;

import com.example.temiapplication.data.model.CallRequest;
import com.example.temiapplication.data.model.CallResponse;
import com.example.temiapplication.data.model.QuestionRequest;
import com.example.temiapplication.data.model.QuestionResponse;
import com.example.temiapplication.data.model.QuizAnswerRequest;
import com.example.temiapplication.data.model.QuizAnswerResponse;
import com.example.temiapplication.data.model.QuizQuestion;
import com.example.temiapplication.data.model.StatisticsSummary;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface TemiApiService {
    @POST("api/calls")
    Call<CallResponse> createCall(@Body CallRequest request);

    @POST("api/questions")
    Call<QuestionResponse> askQuestion(@Body QuestionRequest request);

    @GET("api/quizzes/current")
    Call<QuizQuestion> getCurrentQuiz();

    @POST("api/quizzes/answers")
    Call<QuizAnswerResponse> submitQuizAnswer(@Body QuizAnswerRequest request);

    @GET("api/statistics/summary")
    Call<StatisticsSummary> getStatisticsSummary();
}
