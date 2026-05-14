package com.kidsFriend.data.model;

import com.google.gson.annotations.SerializedName;

public class ChatAiResponse {
    public String reply;

    @SerializedName("created_at")
    public String createdAt;
}
