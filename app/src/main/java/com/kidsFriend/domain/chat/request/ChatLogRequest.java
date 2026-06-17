package com.kidsFriend.domain.chat;

public class ChatLogRequest {
    public String clientId;
    public String robotId;
    public String question;
    public String answer;
    public String chatType;

    public ChatLogRequest(String clientId, String robotId, String question, String answer, String chatType) {
        this.clientId = clientId;
        this.robotId = robotId;
        this.question = question;
        this.answer = answer;
        this.chatType = chatType;
    }
}
