package com.kidsFriend.domain.chat;

import com.google.gson.annotations.SerializedName;

public class ChatAiResponse {
    public String reply;

    // ⚠️ BE(AiChatResponse)는 @JsonProperty("created_at")로 snake_case를 내려준다.
    //    따라서 FE도 반드시 "created_at" 키로 매칭해야 한다. (지우면 createdAt이 null이 됨)
    @SerializedName("created_at")
    public String createdAt;
}
