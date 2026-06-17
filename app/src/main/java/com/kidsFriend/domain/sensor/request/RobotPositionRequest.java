package com.kidsFriend.domain.sensor;

/** 테미 현재 위치 보고 본문. (Temi SDK Position: x, y, yaw, tiltAngle) */
public class RobotPositionRequest {
    public String robotId;
    public float x;
    public float y;
    public float yaw;
    public int tiltAngle;

    public RobotPositionRequest(String robotId, float x, float y, float yaw, int tiltAngle) {
        this.robotId = robotId;
        this.x = x;
        this.y = y;
        this.yaw = yaw;
        this.tiltAngle = tiltAngle;
    }
}
