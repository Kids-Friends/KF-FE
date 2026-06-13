package com.kidsFriend.data.model;

public class ChatAiResponse {
    public String reply;
    // BE(AiChatResponse)는 createdAt(camelCase)로 내려주므로 키 이름 그대로 매칭한다.
    public String createdAt;
}
