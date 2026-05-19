package com.kidsFriend.data.model;

import com.google.gson.annotations.SerializedName;

public class SensorEventRequest {
    @SerializedName("robotId")   private String robotId;
    @SerializedName("eventType") private String eventType;
    @SerializedName("payload")   private Object payload;

    public SensorEventRequest(String robotId, String eventType, Object payload) {
        this.robotId = robotId;
        this.eventType = eventType;
        this.payload = payload;
    }

    public String getRobotId()   { return robotId; }
    public String getEventType() { return eventType; }
    public Object getPayload()   { return payload; }
}
