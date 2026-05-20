package com.kidsFriend.data.model;

public class CallResponse {
    public String callsId;
    public String robotId;
    public String clientId;
    public String reason;
    public String status;

    public String getDisplayCallId() {
        return callsId;
    }
}
