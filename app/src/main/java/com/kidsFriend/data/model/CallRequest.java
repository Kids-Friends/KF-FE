package com.kidsFriend.data.model;

public class CallRequest {
    public String robotId;
    public String clientId;
    public String reason;

    public CallRequest(String reason) {
        this.reason = reason;
    }

    public CallRequest(String robotId, String clientId, String reason) {
        this.robotId = robotId;
        this.clientId = clientId;
        this.reason = reason;
    }
}
