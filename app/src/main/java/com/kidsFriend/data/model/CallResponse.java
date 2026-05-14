package com.kidsFriend.data.model;

public class CallResponse {
    public boolean success;
    public String callId;
    public String callsId;
    public String robotId;
    public String clientId;
    public String message;
    public String reason;
    public String status;

    public CallResponse(boolean success, String callId, String message) {
        this.success = success;
        this.callId = callId;
        this.message = message;
    }

    public String getDisplayCallId() {
        if (callsId != null && !callsId.trim().isEmpty()) {
            return callsId;
        }
        return callId;
    }
}
