package com.kidsFriend.domain.zone;

public class ZoneInfo {
    public String zoneId;
    public String name;
    public String description;
    public String nearbyLocations;
    public String crowdLevel;
    public String guideHint;

    public ZoneInfo(
            String zoneId,
            String name,
            String description,
            String nearbyLocations,
            String crowdLevel,
            String guideHint
    ) {
        this.zoneId = zoneId;
        this.name = name;
        this.description = description;
        this.nearbyLocations = nearbyLocations;
        this.crowdLevel = crowdLevel;
        this.guideHint = guideHint;
    }
}
