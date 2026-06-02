package com.kidsFriend.data.model;

public class ChatAiRequest {
    public String message;
    public String clientId;

    public ChatAiRequest(String message) {
        this.message = message;
    }

    public ChatAiRequest(String message, String clientId) {
        this.message = message;
        this.clientId = clientId;
    }
}
