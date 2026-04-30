package com.example.temiapplication.data.model;

public class VoiceQuestionRequest {
    public String rawText;
    public String reconstructedText;

    public VoiceQuestionRequest(String rawText, String reconstructedText) {
        this.rawText = rawText;
        this.reconstructedText = reconstructedText;
    }
}
