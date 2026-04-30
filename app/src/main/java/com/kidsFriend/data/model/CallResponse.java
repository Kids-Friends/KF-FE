package com.kidsFriend.data.model;

public class CallResponse {
    public boolean success;
    public String callId;
    public String message;

    public CallResponse(boolean success, String callId, String message) {
        this.success = success;
        this.callId = callId;
        this.message = message;
    }
}
