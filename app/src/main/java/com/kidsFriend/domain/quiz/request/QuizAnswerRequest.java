package com.kidsFriend.domain.quiz.request;

public class QuizAnswerRequest {
    public String quizId;
    public String selectedAnswer;

    public QuizAnswerRequest(String quizId, String selectedAnswer) {
        this.quizId = quizId;
        this.selectedAnswer = selectedAnswer;
    }
}