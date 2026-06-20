package com.kidsFriend.domain.greeting.response;

public class IntentResponse {
    public String intent;
    public String confidence;

    public IntentResponse(String intent, String confidence) {
        this.intent = intent;
        this.confidence = confidence;
    }
}
