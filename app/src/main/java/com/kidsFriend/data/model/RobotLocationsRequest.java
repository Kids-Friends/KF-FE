package com.kidsFriend.data.model;

import java.util.List;

/** 테미에 저장된 장소 목록/홈 위치 보고 본문. (Temi SDK getLocations) */
public class RobotLocationsRequest {
    public String robotId;
    public List<String> locations;
    public String homeName;

    public RobotLocationsRequest(String robotId, List<String> locations, String homeName) {
        this.robotId = robotId;
        this.locations = locations;
        this.homeName = homeName;
    }
}
