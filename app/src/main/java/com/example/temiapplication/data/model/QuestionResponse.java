package com.example.temiapplication.data.model;

public class QuestionResponse {
    public boolean success;
    public String answer;

    public QuestionResponse(boolean success, String answer) {
        this.success = success;
        this.answer = answer;
    }
}
