package com.kidsFriend.domain.chat.response;

public class QuestionResponse {
    public boolean success;
    public String answer;
    public String createdAt;

    public QuestionResponse(boolean success, String answer) {
        this.success = success;
        this.answer = answer;
    }

    public QuestionResponse(boolean success, String answer, String createdAt) {
        this.success = success;
        this.answer = answer;
        this.createdAt = createdAt;
    }
}