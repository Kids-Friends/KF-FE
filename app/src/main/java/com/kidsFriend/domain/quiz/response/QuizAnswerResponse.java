package com.kidsFriend.domain.quiz;

public class QuizAnswerResponse {
    public boolean correct;
    public String message;

    public QuizAnswerResponse(boolean correct, String message) {
        this.correct = correct;
        this.message = message;
    }
}
